# Identidade de Serviço (service-to-service)

> **Status:** implementado no ACS (ACS-030) — consumidores em adoção
> **Origem:** E2E Sessão 2, defeitos C-18 / C-19 e a dívida `hist:write`
> **Contrato:** `POST /oauth2/token/service` no acs-service

---

## 1. O problema que isto resolve

Até aqui, toda chamada service-to-service **propagava o JWT do chamador**
(`feignAuthPropagationInterceptor`, presente em PAC e EDGE). Três consequências:

1. **Authorities que o usuário não deveria ter.** `CUIDADORA` precisava de `hist:write` no
   token só porque o PAC grava audit facts durante o `:publish`. Um cuidador passava a poder
   escrever fato de auditoria arbitrário.
2. **Chamadas impossíveis.** O heartbeat do EDGE usa token de dispositivo, que nunca teria
   `gest.detection-profile.read` — o GEST respondia 403 (C-19). Pior: o `@CircuitBreaker` sem
   `ignoreExceptions` traduzia o 403 em "degradado", então a falha era **silenciosa**.
3. **Identidade de serviço inexistente.** `edge.device` e `edge.internal` são exigidas por
   endpoints do EDGE mas **ninguém as emite** (C-18).

A identidade de serviço separa "quem pediu" de "quem está chamando": o EDGE chama o GEST
**como edge-service**, não como o cuidador ou o dispositivo que originou a requisição.

---

## 2. Contrato

### Requisição

```http
POST /oauth2/token/service
Content-Type: application/json

{
  "clientId":     "edge-service",
  "clientSecret": "<segredo>",
  "tenantId":     "e2e00000-0000-0000-0000-000000000001"
}
```

### Resposta (200)

```json
{
  "accessToken":  "<JWT RS256>",
  "refreshToken": null,
  "tokenType":    "Bearer",
  "expiresIn":    900,
  "tenant":       "e2e00000-...",
  "sub":          null,
  "papel":        null,
  "authorities":  ["gest.detection-profile.read"]
}
```

**401 para toda rejeição** — cliente desconhecido, segredo inválido, cliente desabilitado,
sem authorities configuradas e tenant inativo são indistinguíveis para o chamador, para que o
endpoint não sirva de enumeração. O motivo real vai no log em nível `debug`.

### Claims do token emitido

| Claim | Valor |
|---|---|
| `sub` | o `clientId` (não um cuidador) |
| `token_type` | `service` — permite a um resource server distinguir dos tokens de cuidador |
| `tenant_id`, `tenant`, `empresaId` | o tenant **solicitado** |
| `nome` | o `clientId` (snapshot de actor em audit facts) |
| `authorities` | conjunto fixo configurado para o cliente |
| `papel` | ausente — serviço não tem papel de cuidador |

**Não há refresh token.** Ao expirar, o cliente reautentica com o segredo.

---

## 3. Por que o token é escopado por tenant

Não é client-credentials puro. Os serviços roteiam o `search_path` do Postgres pelo claim de
tenant (ver `MultiTenantConnectionProvider`), então um token sem tenant chegaria ao schema
errado. O cliente pede um token **para o tenant da requisição que está servindo**, e o cache
é por `(clientId, tenant)`.

O ACS valida que o tenant existe e está `ACTIVE` antes de emitir.

---

## 4. Registro de clientes

Config-driven, em `acs/application.yml`:

```yaml
acs:
  service-clients:
    edge-service:
      secret-hash: ${EDGE_CLIENT_SECRET_HASH:<bcrypt do segredo de dev>}
      authorities:
        - gest.detection-profile.read
    pac-service:
      secret-hash: ${PAC_CLIENT_SECRET_HASH:...}
      authorities:
        - hist:write
```

- `secret-hash` é **BCrypt**, nunca segredo em claro. Hash em branco **desabilita** o cliente
  (fail closed) — env var ausente rejeita, não aceita qualquer coisa.
- As authorities devem casar **verbatim** com o que o serviço-alvo exige. O `hist-service` é o
  único que aplica o prefixo `SCOPE_` por conta própria, então aqui se escreve `hist:write`.
- Rotação de segredo = trocar a env var e reiniciar. Migrar para uma tabela
  `neuroassist_global.service_client` depois só muda a consulta dentro do
  `ServiceTokenService`; o contrato do token não muda.

**Segredo de dev:** `dev-service-secret` (o hash default no yml). Nunca em produção.

---

## 5. Como um consumidor adota

Duas peças no serviço consumidor:

1. **`ServiceTokenProvider`** — obtém e **cacheia** o token por tenant, renovando antes de
   expirar (margem recomendada: 60 s). Nunca logar o token.
2. **`RequestInterceptor` por cliente Feign** — aplicado só ao cliente que precisa de
   identidade de serviço.

⚠️ **Armadilha de ordem:** o `feignAuthPropagationInterceptor` é global e já adicionou o
header `Authorization` do chamador. `RequestTemplate.header()` **acrescenta**, não substitui.
O interceptor de identidade de serviço precisa:

```java
template.removeHeader("Authorization");
template.header("Authorization", "Bearer " + serviceToken);
```

e rodar **depois** do global.

⚠️ **Ordenação — a receita óbvia não funciona.** `@Order` num método `@Bean` que devolve um
**lambda** é invisível para o `AnnotationAwareOrderComparator.sort(List<RequestInterceptor>)`
que o `FeignClientFactoryBean` usa: o comparator lê a ordem da *instância*, e um lambda não
carrega a anotação do método que o criou. O resultado é ordem indefinida — e, como o efeito é
"às vezes o header do chamador sobrevive", a falha é silenciosa e intermitente.

**Os dois interceptors precisam ser classes reais implementando `Ordered`:**

```java
class AuthPropagationInterceptor implements RequestInterceptor, Ordered {
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE - 1000; }
}

class ServiceIdentityRequestInterceptor implements RequestInterceptor, Ordered {
    public int getOrder() { return Ordered.LOWEST_PRECEDENCE; }
}
```

**Escopo por cliente:** para o interceptor valer só num `@FeignClient`, coloque-o numa classe
de configuração **sem** `@Configuration` (para não ser varrida pelo component scan) e
referencie-a em `@FeignClient(configuration = GestServiceIdentityFeignConfig.class)`.

**Prove que funcionou.** Um teste que monte o `RequestTemplate` já com o header do chamador,
aplique os dois interceptors na ordem que o Spring produz
(`AnnotationAwareOrderComparator.sort`) e afirme que `headers().get("Authorization")` tem
**exatamente um** valor, o do serviço.

**Fail-closed:** se o token de serviço não puder ser obtido, a chamada deve falhar de forma
visível. Não caia de volta no token do chamador — foi exatamente esse fallback silencioso que
transformou o 403 do C-19 em "degradado".

---

## 6. Adoção

| Par | Status | Authority |
|---|---|---|
| EDGE → GEST (profile pointer / heartbeat) | ✅ **adotado** — 138 testes (+16), archunit + integrationTest verdes | `gest.detection-profile.read` |
| PAC → HIST (audit facts) | ✅ **adotado** — 177 testes (+17), archunit verde. `hist:write` **removido** do `RoleAuthorityPolicy` | `hist:write` |
| EVNT → HIST | **N/A hoje** — `AuditTrailClient` é stub vazio e o adapter ativo é `AuditTrailNoOpAdapter`; adota quando o adapter real existir | `hist:write` |
| ACS → HIST | pendente — `HistAuditClient` também é stub vazio (causa raiz de AUD-01/02) | `hist:write` |

Chamadas que **continuam** propagando o token do chamador, por serem decisões tomadas *em
nome do usuário*: PAC→ACS (directory), PAC→GEST (eligibility), PAC→EDGE (validação de
dispositivo), HIST→ACS (patient-scope). O critério: se a autorização depende de *quem pediu*,
propaga; se é o serviço agindo por conta própria, usa identidade de serviço.
