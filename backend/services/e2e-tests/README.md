# e2e-tests — suíte E2E cross-service (E2E-013, Opção A)

**Não é um microserviço.** Não tem `src/main`, não aplica o plugin do Spring Boot, não sobe contexto
nenhum. É um cliente HTTP externo que fala com o stack já de pé, exatamente como o agente de borda
ou o frontend falariam.

## Por que ela existe

Os 7 gates de módulo ficaram verdes enquanto 16 defeitos cross-service estavam vivos
(`docs/architecture/backend/plans/plan-e2e-integration.md` §11). A causa é estrutural: no gate de
módulo o consumidor é testado contra o próprio mock do produtor, e um mock escrito a partir de uma
suposição errada faz o teste passar **precisamente quando a suposição está errada**. Esta suíte não
tem mock nenhum — se o PAC mudar um nome de campo, ela quebra.

Consequência prática ao editar qualquer asserção aqui: **abra o `@RestController` e o record de
resposta do produtor e espelhe campo por campo.** Nunca escreva um nome de campo de memória.

## Como rodar

```bash
# 1. o stack precisa estar no ar
docker compose -f docker/docker-compose.dev.yml up -d

# 2. a suíte
cd backend
./gradlew :services:e2e-tests:e2eTest

# no CI: stack fora do ar vira FALHA em vez de skip
./gradlew :services:e2e-tests:e2eTest -De2e.requireStack=true
```

`./gradlew build` **não** roda nada disto. Toda a suíte carrega a tag JUnit `e2e` (herdada de
`E2ETestBase`); a task `test` exclui a tag e a task `e2eTest` a inclui. Quem clona o repo sem Docker
continua com o build verde.

### Stack fora do ar

O preflight (`StackPreflight`) pinga `/actuator/health` nos 7 serviços antes do primeiro teste e,
quando algum não responde, imprime um banner em nível ERROR dizendo **qual** porta caiu e o comando
para levantá-la — e só então pula. Falhar com `Connection refused` na primeira asserção seria uma
mensagem inútil.

### Apontar para outro ambiente

Tudo é configurável por `-D<prop>` ou variável de ambiente, com default de dev (ver `E2EConfig`):

| Propriedade | Env | Default |
|---|---|---|
| `e2e.acs.url` … `e2e.dash.url` | `E2E_ACS_URL` … | portas do host no compose |
| `e2e.login` / `e2e.senha` | `E2E_LOGIN` / `E2E_SENHA` | usuário de seed de dev |
| `e2e.tenant` | `E2E_TENANT` | `e2e00000-…-0001` (schema `t_emp_dev`) |
| `e2e.requireStack` | `E2E_REQUIRE_STACK` | `false` |

Nenhum segredo real está versionado: `coord@neuroassist.dev` só existe no seed de desenvolvimento.

## O que está coberto (36 cenários)

| Classe | Cenários | O que prova |
|---|---|---|
| `auth/AuthBackboneE2ETest` | AUTH-01..20 | Emissão do token, refresh, JWKS, credencial inválida; **um token do ACS abre uma porta em cada um dos 7 serviços**, e nenhum deles atende anônimo. |
| `auth/TenantIsolationE2ETest` | ISO-01..10 | Header de tenant divergente do claim → 403 `tenant-mismatch` em 6 serviços; PAC sem header → 400 `missing-tenant`; dispositivo não pareado → 403 `device-not-paired`. |
| `crossservice/DetectionProfileProjectionE2ETest` | DEV-01..04 | `GET /gest-service/detection-profile/devices/{id}` — GEST + EDGE (pareamento) + PAC (catálogo) numa resposta só: paciente projetado, três faixas de limiar, origem da faixa por primitivo, ETag `"<perfil>-<catálogo>"` com 304. |
| `crossservice/RecalibrationEtagE2ETest` | FLOW-01..02 | Recalibrar um comando no PAC muda o ETag do perfil do dispositivo no GEST, o ETag antigo deixa de render 304, e a faixa nova aparece projetada no primitivo. |

O cenário AUTH-07..13 é o que mais paga por si: um resource server que configure o conversor de
authorities lendo só `scope` (o default do Spring) devolve 403 aqui — e em nenhum gate de módulo,
porque lá o token é forjado pelo próprio serviço.

## O que ficou de fora, e por quê

Deixar um cenário de fora e dizer isso é melhor que um teste frágil que passa por acidente.

- **Fixture factory com ciclo de vida próprio.** A suíte lê dados semeados (`DevFixtures`) em vez de
  criar paciente/comando/dispositivo/pareamento a cada execução. Um teardown que falha pela metade
  em 4 serviços deixa o ambiente de dev pior a cada rodada. Enquanto não houver um teardown
  confiável, apoiar-se no seed é a opção honesta — e o custo é que apagar essas linhas do banco de
  dev quebra os testes que as usam.
- **`FLOW-01` escreve no ambiente.** Ele restaura a sensibilidade original no `finally`, mas
  `calibracaoVersao`/`catalogoVersao` são monótonos e sobem 2 por execução. Por isso nenhuma
  asserção fixa número absoluto — só variação relativa. Não rode contra produção.
- **AUD (auditoria, E2E-004)** — a escrita de fatos no hist-service é assíncrona via JMS e sem um
  identificador de correlação exposto na resposta HTTP não há como afirmar *qual* fato corresponde à
  chamada que o teste acabou de fazer. Um `sleep` + "existe algum fato recente" passaria por
  acidente com qualquer tráfego concorrente.
- **HB (heartbeat, E2E-005) e NTF/DSH (E2E-007/008)** — exigem um dispositivo com token de device e
  um evento recém-criado com destinatário determinístico; ambos caem no problema de fixture acima.
- **Flow-04 (apagamento LGPD)** — destrutivo e irreversível no ambiente de dev compartilhado.
- **Token autenticado sem `tenant_id` → 403 `tenant_claim_missing`** (E2E-021) — não é reproduzível
  por HTTP: o ACS sempre emite o claim. Forjar o token exigiria a chave privada do issuer. Está
  coberto no gate unitário de cada serviço, não aqui.
- **`evnt-service` no isolamento de tenant** — ver ISO-07: ele **ignora** o header e responde pelo
  claim, em vez de recusar como os outros 6. Não há vazamento, mas há divergência de política. O
  teste documenta o comportamento real; não assere um 403 que não acontece.

## Estrutura

```
src/test/java/com/neuroassist/e2e/
  support/
    E2EConfig.java        # URLs, credenciais, tenant — -D / env / default de dev
    StackPreflight.java   # health dos 7 serviços; skip barulhento ou falha no CI
    E2ETestBase.java      # @Tag("e2e"), login uma vez por JVM, specs authed/anonymous
    AuthClient.java       # /oauth2/token, /token/refresh, decode do payload do JWT
    DevFixtures.java      # ids do seed de dev
    Await.java            # polling curto para propagação cross-service
  auth/                   # AUTH-*, ISO-*
  crossservice/           # DEV-*, FLOW-*
```
