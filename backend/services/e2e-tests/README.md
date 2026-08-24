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

## O que está coberto (64 cenários)

| Classe | Cenários | O que prova |
|---|---|---|
| `auth/AuthBackboneE2ETest` | AUTH-01..20 | Emissão do token, refresh, JWKS, credencial inválida; **um token do ACS abre uma porta em cada um dos 7 serviços**, e nenhum deles atende anônimo. |
| `auth/TenantIsolationE2ETest` | ISO-01..10 | Header de tenant divergente do claim → 403 `tenant-mismatch` em 6 serviços; PAC sem header → 400 `missing-tenant`; dispositivo não pareado → 403 `device-not-paired`. |
| `crossservice/DetectionProfileProjectionE2ETest` | DEV-01..04 | `GET /gest-service/detection-profile/devices/{id}` — GEST + EDGE (pareamento) + PAC (catálogo) numa resposta só: paciente projetado, três faixas de limiar, origem da faixa por primitivo, ETag `"<perfil>-<catálogo>"` com 304. |
| `crossservice/RecalibrationEtagE2ETest` | FLOW-01..02 | Recalibrar um comando no PAC muda o ETag do perfil do dispositivo no GEST, o ETag antigo deixa de render 304, e a faixa nova aparece projetada no primitivo. |
| `edge/HeartbeatE2ETest` | HB-01..06 | Cadeia completa de identidade de dispositivo (registrar → matricular → token no ACS → bater ponto); anti-spoofing do `deviceId` no corpo; operador e anônimo recusados; **o ponteiro de perfil sempre degradado** (defeito D-3). |
| `crossservice/AlarmPipelineE2ETest` | NTF-01..07 | Relatório de gesto → evento → despacho, atravessando EVNT+GEST+PAC+ACS+DASH; idempotência por `reportId`; guarda de payload biométrico; lock terminal do veredito. Documenta D-5 e D-6. |
| `crossservice/AuditTrailE2ETest` | AUD-01..04 | O fato assíncrono no HIST, **correlacionado por `payload.eventoId` + `actor.caregiverId`** — nunca por "fato recente"; cadeia de hash (`streamKey`/`sequenceNo`/`prevHash`); fato de confirmação; leitura por id. |
| `dash/CaregiverPanelE2ETest` | DSH-01..07 | Composição do painel: escopo no ACS, liveness no EDGE, histórico no HIST; paciente fora de escopo → 403. Documenta D-1 e D-2. |
| `flows/LgpdErasureFlowE2ETest` | FLOW-04-01..03 | Apagamento LGPD sobre um paciente descartável: anonimização com preservação da identidade, idempotência, e o defeito D-4. |

O cenário AUTH-07..13 é o que mais paga por si: um resource server que configure o conversor de
authorities lendo só `scope` (o default do Spring) devolve 403 aqui — e em nenhum gate de módulo,
porque lá o token é forjado pelo próprio serviço.

## A fixture factory — isolamento, não limpeza

`support/FixtureFactory.java` monta, **pelas APIs públicas**, o cenário que o seed não tem. A regra
que ela segue não é "criar e apagar depois", é **criar uma vez e reencontrar**:

| Recurso | Estratégia | Cresce por rodada? |
|---|---|---|
| Paciente da suíte | `POST /pacientes` com `Idempotency-Key` **fixo** — o PAC responde 200 com o mesmo recurso em toda rodada seguinte | não |
| Comando publicado | procurado no catálogo ativo pelo rótulo; só criado (draft→validate→publish) quando falta | não |
| Dispositivo | registrado + matriculado a cada rodada (o `clientSecret` só existe na resposta do enroll), **aposentado** no encerramento da JVM | 1 linha, inerte |
| Paciente do fluxo LGPD | novo a cada rodada — o apagamento é terminal, um paciente reutilizado não prova a transição duas vezes | 1 linha, anonimizada pelo próprio cenário |

Por que não há teardown geral: um teardown que falha pela metade em 4 serviços é exatamente o
problema que a suíte quis evitar. O único teardown que existe é `aposentar(deviceId)`, que trata
`409 edge.device-retired` como sucesso (o estado desejado já vale) e nunca derruba a rodada.

**Nada do seed é mutado.** `coord@neuroassist.dev`, o dispositivo `aa4b420e…` e o paciente
`0cd405c6…` são só lidos. O pareamento é exclusivo por paciente, e a factory só o aplica sobre o
paciente *dela* — aplicá-lo ao paciente do seed roubaria o dispositivo dos cenários DEV-*. Como o
paciente da suíte é reaproveitado e o dispositivo não, o `409 edge.pairing-conflict` contra o
dispositivo aposentado da rodada anterior é o caso normal: a factory desfaz aquele vínculo e tenta
uma vez mais.

Quando um passo falha, a mensagem diz **qual passo, contra qual serviço, em qual URL, e o que veio**
(`FixtureException`). "Expected 200 but was 000" na primeira asserção não diz nada disso.

## Defeitos de produto que esta suíte encontrou

Seis, todos vivos no stack de dev e todos invisíveis nos gates de módulo. Cada um tem um cenário que
assere a **realidade atual** e que fica **vermelho no dia em que o defeito for corrigido** — foi
verificado invertendo cada asserção para a expectativa "corrigida" e confirmando que ela falha.

| # | Onde | O quê | Cenário |
|---|---|---|---|
| D-1 | dash-service `PacClient` | Chama `GET /api/v1/patients/{id}/commands`; o PAC expõe `/api/pac/v1/pacientes/{id}/catalogo-ativo`. `/panel/patients/{id}/overview` **nunca funcionou** em runtime. Três métodos com o mesmo prefixo errado. | DSH-06 |
| D-2 | dash-service `PanelCompositionService.buildSummaryEntry` | **Stub vivo**: `name` é o próprio id e `hasActiveEvent` é a constante `false`. O painel da cuidadora jamais acende para um alarme aberto. | DSH-07 |
| D-3 | edge-service ↔ compose | `neuroassist.gest.url` lê `GEST_URL`, que o bloco do edge-service em `docker-compose.dev.yml` não define; o default `lb://gest-service` não passa por load balancer num `@FeignClient(url=…)`. Toda chamada EDGE→GEST falha e o breaker abre: **o `profileDrift` de E2E-018/033 está morto em runtime para todos os dispositivos**. | HB-06 |
| D-4 | pac-service `erasePersonalData` | A resposta do `DELETE` declara `cuidadorResponsavelIds: []`, mas o `GET` seguinte devolve o vínculo intacto. O vínculo *deve* sobreviver; o errado é a resposta, que informa uma remoção que não houve. | FLOW-04-03 |
| D-5 | evnt-service `GlobalExceptionHandler` | Não trata `AccessDeniedException`: negar autorização vira `500 internal_error.unhandled` com detalhe "Access Denied". Os outros seis respondem 403 com código estável. Um retry automático sobre 5xx é o comportamento errado aqui. | NTF-04 |
| D-6 | acs-service diretório + evnt-service | `GET /directory/caregivers/{id}/patient-scope` responde **200 com lista vazia** para uma cuidadora inexistente. O adapter do EVNT trata só `404` como "não conheço", então `isKnownCaregiver` devolve `true` para qualquer UUID e a guarda de EVNT-023 nunca dispara: **qualquer id entra como ator do fato `evento.confirmado`**, que é append-only e encadeado por hash. | NTF-07 |

D-6 é a armadilha nº1 do repositório em estado puro: o gate do EVNT fica verde porque o mock do
produtor responde 404 — passa **exatamente porque** a suposição está errada.

## O que ficou de fora, e por quê

Deixar um cenário de fora e dizer isso é melhor que um teste frágil que passa por acidente.

- **`FLOW-01` escreve no ambiente.** Ele restaura a sensibilidade original no `finally`, mas
  `calibracaoVersao`/`catalogoVersao` são monótonos e sobem 2 por execução. Por isso nenhuma
  asserção fixa número absoluto — só variação relativa. Não rode contra produção.
- **`GET /api/v1/events/active` não é assertável.** `EventQueryService` devolve
  `findByFeedback(NAO_AVALIADO).get(0)` — sem ordenação. Com mais de um evento aberto, qual deles
  aparece é arbitrário, então nenhum cenário pode afirmar que *o seu* evento está lá. Os cenários NTF
  encerram os eventos que criam justamente para não engordar essa lista. É contrato subespecificado,
  não comportamento errado, e por isso não virou cenário de defeito.
- **Apagamento LGPD no hist-service** (`DELETE /api/v1/hist/patients/{id}/personal-data`) — exige a
  authority `hist:erase`, que o operador de dev não tem. O que está coberto é o apagamento pelo PAC.
- **Token autenticado sem `tenant_id` → 403 `tenant_claim_missing`** (E2E-021) — não é reproduzível
  por HTTP: o ACS sempre emite o claim. Forjar o token exigiria a chave privada do issuer. Está
  coberto no gate unitário de cada serviço, não aqui.
- **`evnt-service` no isolamento de tenant** — ver ISO-07: ele **ignora** o header e responde pelo
  claim, em vez de recusar como os outros 6. Não há vazamento, mas há divergência de política. O
  teste documenta o comportamento real; não assere um 403 que não acontece.
- **Opção B (Newman).** Descartada: manteria o mesmo contrato duplicado em JSON, fora do compilador.

## Estrutura

```
src/test/java/com/neuroassist/e2e/
  support/
    E2EConfig.java        # URLs, credenciais, tenant — -D / env / default de dev
    StackPreflight.java   # health dos 7 serviços; skip barulhento ou falha no CI
    E2ETestBase.java      # @Tag("e2e"), login uma vez por JVM, specs authed/anonymous
    AuthClient.java       # /oauth2/token, /token/refresh, claims do JWT
    DevFixtures.java      # ids do seed de dev (só leitura)
    FixtureFactory.java   # dispositivo + paciente + comando montados pelas APIs
    FixtureException.java # falha de montagem — distinta de falha de asserção
    Await.java            # polling sobre predicado específico, para o que é assíncrono
  auth/                   # AUTH-*, ISO-*
  crossservice/           # DEV-*, FLOW-01..02, NTF-*, AUD-*
  edge/                   # HB-*
  dash/                   # DSH-*
  flows/                  # FLOW-04-*
```
