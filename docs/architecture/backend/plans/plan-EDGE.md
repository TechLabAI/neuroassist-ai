<!-- OUTPUT-SCHEMA
- Status
- §0 Leitura executiva
- §1 Achados
- §2 Estratégia
- §3 Prioridades
- §4 Riscos
- §5 Critérios de aceite
-->

# Plano — EDGE (edge-service)

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service` · Port: 8087
> Package: `com.neuroassist.edge` · Multi-tenant: schema-per-empresa · Messaging: disabled
> Atualizado: 2026-08-22 · Pipeline phase: **execution** (M4 — INCREMENTAL)

---

## Status

**Tipo:** INCREMENTAL — gaps isolados: stubs vivos + BDD em dry-run
**Resumo:** P1..P10 concluídos (score real 75/100). Dois gaps restam: (1) stubs vivos que
lançam `UnsupportedOperationException` em runtime (`ProfilePointerController`,
`ProfilePointerService`, DTOs órfãos); (2) ~38 cenários BDD nunca executaram
(`CucumberIT` com `dry-run=true`, 118 step definitions com `PendingException`). Este plano
fecha E2E-005 (cenários HB-02..05) com validação real e zera os stubs vivos.

---

## Status das prioridades

- P1..P10 — ✅ DONE (ver histórico abaixo)
- P11 — ✅ DONE (commit f835f0d — 4 arquivos REST removidos, ProfilePointerUseCase/Service reescritos como colaborador, authority `edge.profile-pointer.read` removida do ACS e dos catálogos ArchUnit)
- P12 — pendente (implementar SharedSteps + HeartbeatLivenessSteps + ProfilePointerDriftSteps; dry-run ainda ON)
- P13 — pendente (implementar DeviceRegistrationEnrollmentSteps + DevicePairingSteps + desligar dry-run + full gate)

---

## Histórico P1..P10 (não replanejar)

| P | Título | Status |
|---|---|---|
| P1 | Scaffold + domain model + controllers skeleton | ✅ DONE |
| P2 | Operações de domínio (golden path) | ✅ DONE |
| P3 | Persistência + migrations | ✅ DONE |
| P4 | Integração externa Feign GEST + retire | ✅ DONE |
| P5 | OAuth2 security + multi-tenancy | ✅ DONE |
| P6 | Domain events in-process | ✅ DONE |
| P7 | Observability | ✅ DONE |
| P8 | BDD infrastructure + 120/120 unit tests | ✅ DONE (mas dry-run=true, cenários nunca executaram — fechado por LIGHT, invalidado pelo BASELINE 2026-08-22) |
| P9 | Hardening (optimistic lock + pagination + ETag) | ✅ DONE |
| P10 | score 85→100 (integrationTest + sync) | pendente — substituído por P11..P13 neste replanejamento |

**Já corrigido nesta sessão (não replanejar):**
- `DetectionProfilePort` retorna `ProfilePointer(version, degraded)` com três estados: `of(version)`, `noActiveProfile()`, `unknown()`
- GEST 404 traduzido para "sem perfil ativo" (não degradado)
- `HeartbeatService.doRecordHeartbeat()` deriva `profileDrift` como `null` (degradado) / `false` (sem perfil) / comparação
- Gate `test` (160/160) e `architectureCheck` verdes

---

## §0 Leitura executiva

O `edge-service` compila, passa 160/160 testes unitários e ArchUnit. A lógica de
`HeartbeatService` com `ProfilePointer` de três estados já está correta. O que falta são
dois itens: stubs mortos que não têm endpoint real na api-contract (o `ProfilePointerController`
em `/edge-service/profile-pointer` lança 501 e nunca foi mapeado) e ~38 cenários BDD que
passam a suíte unitária porque o runner está em dry-run.

O plano remove o código morto (P11), implementa os steps das features de heartbeat e
profile-pointer com WireMock para GEST (P12 — fecha E2E-005 HB-02..05) e completa os steps
de registration/pairing antes de desligar o dry-run (P13 — gate BDD verde por inteiro).

---

## §1 Achados

Score real 75/100 (ES registrava 100/100). Dois gaps isolados bloqueiam o fechamento: stubs vivos com `UnsupportedOperationException` e ~38 cenários BDD que nunca executaram por causa do `dry-run=true` no runner.

### Pontos fortes

- `DetectionProfilePort.ProfilePointer` com três estados corretos (corrigido nesta sessão)
- `HeartbeatService` e `LivenessService` derivam `profileDrift` corretamente
- Testcontainers, `CucumberSpringContext`, WireMock, `TestJwtFactory` existem (EDGE-019 7/8)
- `DeviceRegistrationController.resolveTenant()` já implementa `GET /api/v1/edge/devices/{deviceId}/tenant` — a responsabilidade do ProfilePointerController já está coberta
- Gate `test` 160/160, ArchUnit OK, build OK

### Gaps

| # | Gap | Severidade |
|---|---|---|
| G1 | `ProfilePointerController` em `/edge-service/profile-pointer` lança `UnsupportedOperationException` — endpoint 501 em runtime | BLOQUEADOR (stub vivo) |
| G2 | `ProfilePointerUseCase`, `ProfilePointerService`, `ProfilePointerRequest`, `ProfilePointerResponse`, `LivenessRequest` — DTOs/interfaces com campos `TODO` sem uso real | MORTO (interferência de ArchUnit e manutenção) |
| G3 | `CucumberIT.java` com `EXECUTION_DRY_RUN_PROPERTY_NAME = "true"` — 118 steps com `PendingException`, ~38 cenários nunca executaram | BLOQUEADOR (EDGE-020 falsamente DONE) |
| G4 | E2E-005 HB-02..05 bloqueados — cenários `profileDrift`, `GEST degraded`, `GEST no-active-profile`, `circuit breaker` precisam de steps implementados | BLOQUEADOR (E2E open) |

### Aderência às regras (RULE-01..09)

- RULE-01/02 (no Spring/JPA in domain): OK — `ProfilePointer` é record puro em `port/out`
- RULE-03 (@Transactional somente em application.service): OK
- RULE-04/05 (api ↔ infrastructure): OK
- RULE-06 (@FeignClient em infrastructure.clients): OK
- RULE-07 (TenantContextHolder.require()): OK — `HeartbeatService` e `LivenessService` resolvem tenant do JWT antes da transação
- RULE-08 (open-in-view=false): OK
- RULE-09 (base package `com.neuroassist.edge`): OK

### Dívida técnica registrada (não bloqueia este plano)

- `EdgePairingClient` no `gest-service` usa `url = "${edge.pairing.url:http://localhost:8090}"` — porta do DASH, `localhost` é o próprio GEST no container. Registrada em `plan-e2e-integration.md §11.2`. Corrigir no gest-service, não no edge-service.

---

## §2 Estratégia

Tipo de planejamento: **INCREMENTAL** — os gaps são isolados e a implementação existente está correta.

| P | Título | Motivo | Issues |
|---|---|---|---|
| P11 | Remover stub 501 e consolidar o ponteiro | Deletar a superfície REST não declarada; reescrever `ProfilePointerService` como colaborador (spec c4-component); remover a authority órfã do ACS | EDGE-019 (item restante: sem 501 em runtime) |
| P12 | BDD steps — heartbeat + profile-pointer (dry-run ON) | Implementar SharedSteps + HeartbeatLivenessSteps + ProfilePointerDriftSteps enquanto DeviceRegistration e Pairing steps ainda são PendingException; safe com dry-run | EDGE-020, E2E-005 |
| P13 | BDD steps — registration + pairing + desligar dry-run + full gate | Completar os 2 step files restantes, remover dry-run=true, validar todos ~38 cenários reais | EDGE-019, EDGE-020 |

**Dependência de ordem obrigatória:** P11 → P12 → P13 (sequencial; P12 não pode ser executada enquanto os stubs vivos causam erro de contexto Spring; P13 não pode desligar o dry-run enquanto DeviceRegistration/Pairing steps são stubs).

---

## §3 Prioridades

---

### Prioridade 11 — Remover stubs vivos orphãos (ProfilePointer + DTOs TODO)

**Status:** ✅ DONE (2026-08-23, commit f835f0d)
**Issues cobertas:** EDGE-019 (item 8/8 — sem 501 em runtime)

#### Objetivo

Eliminar 6 arquivos Java que não têm endpoint real na api-contract, lançam
`UnsupportedOperationException` em runtime e contêm campos `TODO` não preenchidos.
O endpoint `GET /api/v1/edge/devices/{deviceId}/tenant` já existe em
`DeviceRegistrationController.resolveTenant()`. O `ProfilePointerController` em
`/edge-service/profile-pointer` é código morto gerado pelo scaffold que nunca foi mapeado
para um endpoint real.

#### Problema identificado

`ProfilePointerController.currentPointerForTenant()` lança
`UnsupportedOperationException` para qualquer requisição `GET /edge-service/profile-pointer`,
resultando em HTTP 501. O endpoint não aparece na api-contract (`api-contract.md §9`).
As interfaces e DTOs associados têm campos `TODO` e zero chamadores reais no código de
produção.

#### Correções mandatórias

1. Deletar `ProfilePointerController.java`
2. Deletar `ProfilePointerUseCase.java`
3. Deletar `ProfilePointerService.java`
4. Deletar `ProfilePointerRequest.java`
5. Deletar `ProfilePointerResponse.java`
6. Deletar `LivenessRequest.java`

Nenhuma dessas classes é referenciada por nenhum outro arquivo de produção (verificado via
grep: zero referências externas). O `ProfilePointerService` é registrado via `@Service`,
portanto ao deletar o controller e a interface, o bean some do contexto Spring sem efeitos
colaterais.

> **Correção de escopo — autopilot 2026-08-22.** O plano original justificava a remoção dizendo
> que o endpoint "já existe em `DeviceRegistrationController.resolveTenant()`". Não é o mesmo:
> `GET /api/v1/edge/devices/{deviceId}/tenant` resolve **device→tenant**, enquanto
> `/edge-service/profile-pointer` seria a versão do perfil de detecção de um tenant. A razão
> correta para remover é outra, e mais simples: **o `api-contract.md` declara 11 endpoints e
> nenhum deles é esse.** O controller é scaffolding do gerador para uma superfície que a spec
> nunca pediu.
>
> **O que a spec pede de fato** (`diagrams/c4-component.puml:24` e `:58`) é um
> `ProfilePointerService` como *colaborador do HeartbeatService* — "fetches active profile
> version pointer from GEST; computes drift" — e não um controller. Essa lógica hoje está
> duplicada em `HeartbeatService` e `LivenessService`. Extraí-la para o service honra a spec e
> remove a duplicação; deletar o service perde as duas coisas.
>
> **Consequência que o plano original não previu:** o ACS emite `edge.profile-pointer.read` para
> `COORDENADOR_CLINICA` e `CUIDADORA` (`RoleAuthorityPolicy` linhas 61 e 87), e a authority está
> no catálogo pinado do `CrossAspectArchTest` (linha 51). Apagar o endpoint sem apagar a
> authority deixa um privilégio emitido a todo cuidador que não guarda coisa alguma — o espelho
> exato da família C-18 (`edge.device`/`SVC_EVNT` exigidas e emitidas por ninguém), agora com os
> polos invertidos.

#### Alvos de alteração

**Deletar** (superfície REST que o `api-contract.md` não declara):

- `api/controller/ProfilePointerController.java`
- `api/dto/ProfilePointerRequest.java`
- `api/dto/ProfilePointerResponse.java`
- `api/dto/LivenessRequest.java`

**Reescrever, não deletar** (a spec pede o colaborador; o que existe hoje é um stub 501):

- `application/port/in/ProfilePointerUseCase.java` → vira um port **de saída** ou um domain
  service com assinatura real: dado `deviceId`/`tenantId` e a versão que o dispositivo roda,
  devolve o ponteiro e o drift.
- `application/service/ProfilePointerService.java` → recebe a lógica hoje duplicada em
  `HeartbeatService` e `LivenessService`: chamar `DetectionProfilePort` e derivar `profileDrift`
  com a semântica de três estados (`null` desconhecido / `false` sem perfil publicado /
  comparação).

**Remover a authority órfã** (senão sobra privilégio sem endpoint):

- `acs-service/.../RoleAuthorityPolicy.java` — retirar `"edge.profile-pointer.read"` das duas
  listas (COORDENADOR_CLINICA e CUIDADORA)
- `edge-service/src/test/.../CrossAspectArchTest.java` — retirar do catálogo pinado

#### Contrato operacional

**Deve existir após P11:**
- `./gradlew :services:edge-service:build` exit 0
- `./gradlew :services:edge-service:architectureCheck` exit 0
- `./gradlew :services:edge-service:test` 160/160 PASS
- Zero ocorrências de `UnsupportedOperationException` ou `PendingException` lançadas por código de produção

**Não deve existir:**
- Nenhuma classe em `com.neuroassist.edge` com `throw new UnsupportedOperationException`
- Nenhum endpoint `/edge-service/profile-pointer` registrado no contexto Spring

#### Sequência de execução

- Fase 1: verificar referências (grep por `ProfilePointerController`, `ProfilePointerRequest`,
  `ProfilePointerResponse`, `LivenessRequest` em `src/main` — confirmar zero referências)
- Fase 2: deletar os 4 arquivos de superfície REST
- Fase 3: reescrever `ProfilePointerUseCase` + `ProfilePointerService` com assinatura real, e
  fazer `HeartbeatService` e `LivenessService` delegarem a ele (a derivação de `profileDrift`
  passa a existir num lugar só)
- Fase 4: remover `edge.profile-pointer.read` do `RoleAuthorityPolicy` do ACS e do catálogo
  pinado do `CrossAspectArchTest`
- Fase 5: `./gradlew :services:edge-service:build :services:acs-service:build`
- Fase 6: `./gradlew :services:edge-service:architectureCheck :services:acs-service:architectureCheck`
- Fase 7: `./gradlew :services:edge-service:test :services:acs-service:test` — suíte completa nos
  dois serviços (o ACS é tocado pela remoção da authority)

#### Política de testes

- Nenhum teste novo necessário — testes existentes devem continuar passando
- Verificar que nenhum teste em `src/test` importa as classes deletadas

#### Multi-empresa

Não aplicável — os arquivos deletados não tocam dado de empresa.

#### Migrações Flyway

Não aplicável — nenhuma tabela DB vinculada.

#### Critérios de aceite

- [ ] Os 6 arquivos não existem mais em `src/main`
- [ ] `./gradlew :services:edge-service:build` exit 0
- [ ] `./gradlew :services:edge-service:architectureCheck` exit 0
- [ ] `./gradlew :services:edge-service:test` 160/160 PASS
- [ ] Zero classes em `com.neuroassist.edge` lançam `UnsupportedOperationException`
- [ ] EDGE-019 marcada `DONE 8/8`

#### Riscos

- **Baixo:** `@Service` do `ProfilePointerService` injeta no contexto Spring via `ProfilePointerUseCase` — ao deletar ambos, o contexto não tenta injetar nada. Risco: algum `@Configuration` referencia explicitamente — verificar antes de deletar.
- **Baixo:** algum teste em `src/test` importa `ProfilePointerController` — verificar e deletar junto.

#### Instrução para implementador

Antes de deletar: rodar `grep -rn "ProfilePointerController\|ProfilePointerUseCase\|ProfilePointerService\|LivenessRequest\|ProfilePointerRequest\|ProfilePointerResponse" backend/services/edge-service/src/` e confirmar que as únicas ocorrências são nos próprios 6 arquivos. Se houver referências externas, reportar ao usuário antes de prosseguir. Após deletar, rodar `build`, `architectureCheck`, `test` nessa ordem.

---

### Prioridade 12 — BDD steps: SharedSteps + HeartbeatLiveness + ProfilePointerDrift (dry-run ON)

**Status:** pendente
**Issues cobertas:** EDGE-020 (parcial), E2E-005 (HB-02..05)

#### Objetivo

Implementar os step definitions das features de heartbeat e profile-pointer drift com
chamadas REST reais ao serviço em Testcontainers. Ao final, os cenários das features
`heartbeat-liveness.feature` e `profile-pointer-drift.feature` passam com exit 0.
O `CucumberIT.java` permanece em `dry-run=true` porque `DeviceRegistrationEnrollmentSteps`
e `DevicePairingSteps` ainda têm `PendingException` — desligá-lo agora quebraria esses.

#### Problema identificado

`SharedSteps.java`, `HeartbeatLivenessSteps.java` e `ProfilePointerDriftSteps.java` têm
118 steps com `throw new io.cucumber.java.PendingException()`. Nenhum dos cenários HB-01..HB-N
ou ProfilePointerDrift executa de fato.

Os cenários HB-02..05 (E2E-005) exigem:
- WireMock stub para `GET /api/v1/gest/detection-profiles/active` com versão específica → HB-02 (drift)
- WireMock configurado para simular GEST indisponível (connection refused ou 503) → HB-03/HB-05
- WireMock stub retornando 404 → HB-04 (no active profile)
- `DetectionProfilePort.ProfilePointer` já retorna três estados corretamente — os steps precisam apenas chamar o heartbeat endpoint e validar o response body

#### Correções mandatórias

**SharedSteps.java — implementar todos os steps compartilhados:**

1. `Given the tenant "empresa-test" is active` → criar schema `t_test` via JDBC no Testcontainers PostgreSQL; chamar `TenantContextHolder.set("empresa-test")` antes do teste; usar `@Before` em `CucumberSpringContext` ou no próprio step para garantir schema
2. `Given device "d7b1" is paired to patient "9c2a"` → inserir row em `t_test.dispositivo_edge` com `id='d7b1'`, `paciente_id='9c2a'`, `pairing_state='PAIRED'`
3. `Given the device credential for "d7b1" is valid` → gerar JWT de dispositivo via `TestJwtFactory` com claims `deviceId=d7b1`, `tenant=empresa-test`; armazenar em `TestContext` para uso nos steps `When`
4. `Then the response status is 200/201/409/403/404` → verificar `lastResponse.getStatusCode()`
5. `Given device "d7b1" is in state "retired"` → UPDATE `dispositivo_edge` SET `pairing_state='RETIRED'`
6. Demais steps compartilhados: implementar seguindo o mesmo padrão

**HeartbeatLivenessSteps.java — implementar todos os steps:**

1. `When the edge device "d7b1" sends a heartbeat ...` → `POST /api/v1/edge/heartbeat` via `TestRestTemplate` com Bearer token do device JWT; body `{ "agentVersion": "1.4.2", "runningProfileVersion": 6 }`
2. `And the response body has status "online"/"offline"/"n/a"` → parsear `lastResponse.getBody()` e verificar campo `status`
3. `And the response body ultimaSincronizacao equals the server time` → verificar que `ultimaSincronizacao` está dentro de 2 segundos do `serverTime` do response (ou de `Instant.now()`)
4. `When DASH polls the liveness of device "d7b1"` → `GET /api/v1/edge/devices/d7b1/liveness` via `TestRestTemplate` com Bearer token de operador
5. `Given the last accepted heartbeat for device "d7b1" was <age> seconds ago` → UPDATE `t_test.dispositivo_edge` SET `ultima_sincronizacao = NOW() - INTERVAL '<age> seconds'`
6. `And a liveness transition "online -> offline" is appended with actor "system"` → query `t_test.dispositivo_edge_transition` e verificar existência de row
7. `Given device "d7b1" is online with a known liveness ETag` → derivar ETag esperado ("online" bucket), armazenar; step seguinte faz GET com `If-None-Match`
8. Demais steps: implementar seguindo o padrão de chamada REST + assertion no response

**ProfilePointerDriftSteps.java — implementar todos os steps:**

1. `Given GEST reports the active profile version is 7` → configurar WireMock: `stubFor(get(urlPathEqualTo("/api/v1/gest/detection-profiles/active")).withHeader("X-Tenant-Id", equalTo("empresa-test")).willReturn(aResponse().withStatus(200).withBody("{\"version\":7}")))`
2. `When the edge device "d7b1" sends a heartbeat with runningProfileVersion 7` → `POST /api/v1/edge/heartbeat` com `{"runningProfileVersion":7}`
3. `And the response body currentProfileVersion is 7` → verificar `currentProfileVersion` no JSON response
4. `And the response body profileDrift is false/true` → verificar `profileDrift` no JSON response
5. `Given GEST reports no active profile for the tenant` → WireMock stub retorna `404` para `/api/v1/gest/detection-profiles/active`
6. `Given GEST is unavailable and the circuit breaker is open` → WireMock stub configurado para `CONNECTION_REFUSED` ou `503`; aguardar circuit breaker abrir (enviar N heartbeats para o threshold)
7. `And the response body profilePointerDegraded is true` → verificar campo `profilePointerDegraded`
8. `Given device "d7b1" is registered in tenant "empresa-test"` → dispositivo já existe (sobrepõe com SharedSteps)
9. `And the caller presents a valid service credential` → gerar JWT de serviço via `TestJwtFactory` com claims de serviço
10. `When GEST requests the tenant of device "d7b1"` → `GET /api/v1/edge/devices/d7b1/tenant` via `TestRestTemplate`
11. `And the response body has tenantRef "empresa-test"` → verificar campo `tenantRef`

**Manter `CucumberIT.java` com `dry-run=true`** — DeviceRegistration e Pairing steps ainda têm PendingException; desligar agora os quebraria.

#### Alvos de alteração

- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/steps/SharedSteps.java`
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/steps/HeartbeatLivenessSteps.java`
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/steps/ProfilePointerDriftSteps.java`
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/CucumberIT.java` (NÃO mudar dry-run — arquivo listado apenas para referência)
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/CucumberSpringContext.java` (se precisar ajustar Testcontainers setup)
- `backend/services/edge-service/src/test/resources/application-test.yml` (se precisar ajustar WireMock URL ou Testcontainers)

#### Contrato operacional

**Deve existir após P12:**
- `./gradlew :services:edge-service:test` exit 0 (dry-run ainda ON — 160+N tests PASS, Cucumber não conta falhas)
- HeartbeatLivenessSteps e ProfilePointerDriftSteps sem nenhum `PendingException`
- `CucumberIT.java` ainda com `dry-run=true`
- WireMock configurado para simular GEST: version-200, no-active-404, unavailable-503

**Não deve existir:**
- Nenhum step em `HeartbeatLivenessSteps` ou `ProfilePointerDriftSteps` lançando `PendingException`
- Calls reais a GEST em runtime de teste (toda comunicação via WireMock)

#### Sequência de execução

- Fase 1 (SharedSteps): implementar tenant schema setup, device fixture helper, JWT factory wiring, response assertions comuns
- Fase 2 (HeartbeatLivenessSteps): implementar chamadas `POST /heartbeat` e `GET /liveness`, assertions de corpo e headers
- Fase 3 (ProfilePointerDriftSteps): configurar WireMock GEST stubs (version/404/503) + steps de drift e degraded
- Fase 4: `./gradlew :services:edge-service:test` — confirmar que a suíte passa com dry-run ON

#### Política de testes

- Todos os steps devem usar `TestRestTemplate` injetado via `@SpringBootTest(webEnvironment=RANDOM_PORT)`
- WireMock deve estar rodando na porta configurada em `application-test.yml` (`gest.detection-profile.url`)
- Testcontainers PostgreSQL já configurado em `CucumberSpringContext` — reusar sem criar novo container
- Não criar fixtures inline no step: extrair em métodos `private` do próprio steps file

#### Multi-empresa

- `TenantContextHolder` deve ser setado com `"empresa-test"` antes de qualquer chamada de serviço
- Schema `t_test` deve existir (criado no SharedSteps `Given the tenant...`)
- JWT de device deve carregar claim `tenant=empresa-test` (via `TestJwtFactory`)

#### Migrações Flyway

Não aplicável — schema de teste criado em memória pelo Testcontainers.

#### Critérios de aceite

- [ ] `SharedSteps.java` sem nenhum `PendingException`
- [ ] `HeartbeatLivenessSteps.java` sem nenhum `PendingException`
- [ ] `ProfilePointerDriftSteps.java` sem nenhum `PendingException`
- [ ] `CucumberIT.java` permanece com `dry-run=true`
- [ ] `./gradlew :services:edge-service:test` exit 0
- [ ] Cenário "Drift is flagged when the device runs an older profile" passa (HB-02 / E2E-005)
- [ ] Cenário "Liveness never depends on GEST - heartbeat accepted when GEST is unavailable" passa (HB-03 / E2E-005)
- [ ] Cenário "Heartbeat still succeeds when GEST has no active profile yet" passa (HB-04 / E2E-005)
- [ ] Cenário circuit breaker (HB-05 / E2E-005): heartbeat aceito com `profilePointerDegraded=true` quando GEST 503

#### Riscos

- **ALTO:** Circuit breaker threshold — para HB-05, o CB só abre após N falhas consecutivas. O step `Given GEST is unavailable and the circuit breaker is open` precisa enviar N heartbeats antes do cenário principal. Usar `@Resilience4j` threshold configurável em `application-test.yml` (ex.: `minimumNumberOfCalls: 3`).
- **MÉDIO:** WireMock reset entre cenários — cada cenário deve começar com WireMock limpo. Usar `@After` hook em `CucumberSpringContext` chamando `wireMockServer.resetAll()`.
- **MÉDIO:** Testcontainers startup time — se o PostgreSQL não estiver pronto quando o primeiro cenário rodar, o setup falha. Garantir `@DynamicPropertySource` configurado antes do Spring context inicializar.
- **BAIXO:** ETag derivado de "online"/"offline" bucket — a implementação calcula o ETag via hash do estado derivado; o step precisa derivar o mesmo ETag esperado sem acessar internals. Alternativa: fazer um GET primeiro, capturar o ETag do response header, e usar no segundo GET.

#### Instrução para implementador

Implementar na ordem: SharedSteps → HeartbeatLivenessSteps → ProfilePointerDriftSteps. Não pular SharedSteps — os steps de setup de tenant e fixture de device são pré-condições de todos os outros. Para o circuit breaker (HB-05): configurar `resilience4j.circuitbreaker.instances.detectionProfileCircuitBreaker.minimumNumberOfCalls=3` no `application-test.yml` e enviar 3 heartbeats com WireMock em modo `fault(Fault.CONNECTION_RESET_BY_PEER)` antes do cenário principal. Verificar o endpoint exato do WireMock stub para GEST — usar `application-test.yml` para apontar `gest.detection-profile.url` (ou equivalente) para o WireMock port.

---

### Prioridade 13 — BDD steps: Registration + Pairing + disable dry-run + full gate

**Status:** pendente
**Issues cobertas:** EDGE-019 (DONE 8/8), EDGE-020 (DONE 5/5)

#### Objetivo

Implementar os step definitions das features de registration/enrollment e pairing, remover
`dry-run=true` do `CucumberIT.java` e validar que todos os ~38 cenários passam com exit 0
no gate completo. Esta é a etapa de fechamento — score EDGE sobe de 75 para 100.

#### Problema identificado

`DeviceRegistrationEnrollmentSteps.java` e `DevicePairingSteps.java` têm `PendingException`
em todos os steps. Enquanto tiverem stubs, o dry-run não pode ser desligado (senão todos
esses cenários falham de uma vez).

#### Correções mandatórias

**DeviceRegistrationEnrollmentSteps.java — implementar todos os steps:**

1. `Given a new device with name "device-alpha" ready to register` → preparar `RegisterDeviceRequest` body
2. `When the operator registers the device` → `POST /api/v1/edge/devices` com token de operador (`edge.manage`); armazenar response em `TestContext`
3. `Then the response body has an enrollmentToken` → verificar que `enrollmentToken` está presente no response body (201)
4. `When the device uses the enrollment token to enroll` → `POST /api/v1/edge/devices/{deviceId}/enroll` com token de enrollment; armazenar `clientSecret`
5. `Then the response body has a clientSecret` → verificar campo `clientSecret` presente
6. `Then the enrollment token is no longer valid` → tentar `POST /enroll` com mesmo token → deve retornar 409 ou 401
7. `When the device tries to enroll again with the same token` → chamar `POST /enroll` novamente
8. `Given device "d7b1" is enrolled with a credential` → setup completo via registration→enrollment
9. `When the operator retires device "d7b1"` → `DELETE /api/v1/edge/devices/d7b1` com token de operador
10. `Then the device credential is revoked` → tentar heartbeat com credential revogada → deve retornar 401/403
11. Steps de scope guard: caller sem scope `edge.manage` → 403

**DevicePairingSteps.java — implementar todos os steps:**

1. `When the operator pairs device "d7b1" to patient "9c2a"` → `POST /api/v1/edge/devices/d7b1/pair` com `{"pacienteId":"9c2a"}`
2. `Then device "d7b1" is paired to patient "9c2a"` → verificar response body
3. `When the operator unpairs device "d7b1"` → `DELETE /api/v1/edge/devices/d7b1/pair`
4. `Then device "d7b1" is unpaired` → verificar response; GET device → `pairingState=UNPAIRED`
5. `When the operator tries to pair device "e8c2" to patient "9c2a"` → tentar pairing de segundo device para mesmo paciente → 409 (exclusive pairing)
6. `When the operator pairs a retired device` → setup device retired + tentar pair → 409
7. `When re-pairing device "d7b1"` → pair device já paired para outro paciente → deve revogar credential anterior e retornar nova

**CucumberIT.java — remover dry-run:**

Substituir:
```
@ConfigurationParameter(key = Constants.EXECUTION_DRY_RUN_PROPERTY_NAME, value = "true")
```
Por: remover a linha inteira (sem a annotation — dry-run default é false).

#### Alvos de alteração

- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/steps/DeviceRegistrationEnrollmentSteps.java`
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/steps/DevicePairingSteps.java`
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/CucumberIT.java`
- `backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/CucumberSpringContext.java` (se precisar cleanup entre cenários)
- `docs/architecture/backend/issues/EDGE-019.md` (marcar DONE 8/8)
- `docs/architecture/backend/issues/EDGE-020.md` (marcar DONE 5/5)

#### Contrato operacional

**Deve existir após P13:**
- `./gradlew :services:edge-service:test` exit 0 com todos ~38 cenários PASS
- Zero cenários com status `Pending`, `Undefined` ou `Skipped`
- `CucumberIT.java` sem `EXECUTION_DRY_RUN_PROPERTY_NAME`
- EDGE-019 marcada `DONE 8/8`
- EDGE-020 marcada `DONE 5/5`
- `plan-e2e-integration.md`: E2E-005 HB-02..05 marcados como DONE

**Não deve existir:**
- Nenhuma linha `dry-run=true` em `CucumberIT.java`
- Nenhum `PendingException` em qualquer step file

#### Sequência de execução

- Fase 1 (DeviceRegistrationEnrollmentSteps): implementar todos os steps de registration, enrollment, token-expired, retire, scope-guard
- Fase 2 (DevicePairingSteps): implementar todos os steps de pair, unpair, exclusive-conflict, retired-device, re-pair
- Fase 3 (CucumberIT): remover `@ConfigurationParameter(key = Constants.EXECUTION_DRY_RUN_PROPERTY_NAME, value = "true")`
- Fase 4: `./gradlew :services:edge-service:test` — validar que todos ~38 cenários passam com exit 0
- Fase 5: atualizar EDGE-019 e EDGE-020 para DONE; atualizar E2E-005 em plan-e2e-integration.md

#### Política de testes

- Suíte completa obrigatória — sem deferred, sem `--light`
- Todos os ~38 cenários contados no report do Cucumber (`build/reports/cucumber/cucumber-report.html`)
- `./gradlew :services:edge-service:test` deve sair com exit 0 e o HTML report deve mostrar zero pending/undefined

#### Multi-empresa

- Todos os steps de registration/pairing usam tenant `empresa-test` via `SharedSteps` setup
- Schema `t_test` criado em `SharedSteps` — não recriar

#### Migrações Flyway

Não aplicável — schema de teste em memória via Testcontainers.

#### Critérios de aceite

- [ ] `DeviceRegistrationEnrollmentSteps.java` sem nenhum `PendingException`
- [ ] `DevicePairingSteps.java` sem nenhum `PendingException`
- [ ] `CucumberIT.java` sem linha `EXECUTION_DRY_RUN_PROPERTY_NAME`
- [ ] `./gradlew :services:edge-service:test` exit 0 com todos ~38 cenários PASS
- [ ] Cucumber HTML report: zero `Pending`, zero `Undefined`, zero `Skipped`
- [ ] Cenário "Credential enrollment retorna clientSecret uma única vez" PASS
- [ ] Cenário "Liveness: poll após staleness retorna status=offline" PASS
- [ ] Cenário "Exclusive pairing: segundo device para mesmo paciente retorna 409" PASS
- [ ] EDGE-019 marcada DONE 8/8
- [ ] EDGE-020 marcada DONE 5/5
- [ ] `plan-e2e-integration.md §E2E-005`: HB-02..05 marcados DONE

#### Riscos

- **ALTO:** `enrollment token` é one-time com TTL 15min — nos testes, o token é criado e consumido no mesmo cenário; se o timing do Testcontainers startup for lento, o token pode estar próximo do TTL. Usar TTL de 60min em `application-test.yml` para testes (`neuroassist.edge.enrollment.ttl-minutes=60`).
- **MÉDIO:** Re-pairing rotaciona credential — o step "re-pair" precisa verificar que a credential anterior é revogada antes de checar a nova. Sequenciar: pair → unpair → pair novamente com novo paciente → verificar credential rotacionada.
- **BAIXO:** Contador de cenários — o número exato pode ser 37 ou 39 dependendo de `Scenario Outline` com múltiplas linhas de Examples. Verificar pelo HTML report, não contar manualmente.

#### Instrução para implementador

**Ordem obrigatória dentro do P13:** implementar TODOS os steps de DeviceRegistrationEnrollment e DevicePairing ANTES de remover o dry-run. Verificar com `grep -rn "PendingException" src/test/java/com/neuroassist/edge/bdd/steps/` — deve retornar zero resultados. Somente então remover a annotation do `CucumberIT.java` e rodar `./gradlew :services:edge-service:test`. Rodar a suíte completa sem nenhuma flag de filter ou skip.

---

## §4 Riscos

| Risco | Probabilidade | Impacto | Mitigação |
|---|---|---|---|
| Circuit breaker threshold não configurável em test profile | Média | Alto | Adicionar `resilience4j.circuitbreaker.instances.*.minimumNumberOfCalls=3` em `application-test.yml`; verificar que a chave corresponde ao nome do CB em `HeartbeatService` |
| WireMock não reseta entre cenários — step de um cenário contamina o próximo | Média | Alto | Adicionar `wireMockServer.resetAll()` em `@After` hook em `CucumberSpringContext` |
| Testcontainers PostgreSQL não inicializado quando primeiro step roda | Baixa | Alto | Garantir que Testcontainers está em static initializer (singleton pattern) — já configurado no EDGE, verificar antes de alterar |
| ETag derivado diverge entre implementação e step | Média | Médio | Step faz GET para capturar ETag real, usa-o no GET condicional — não derivar o ETag "de fora" |
| Número real de cenários difere do esperado (~38) | Baixa | Baixo | Verificar pelo HTML report após execução; critério é "zero Pending" não "exatamente 38" |
| `EdgePairingClient` no gest-service aponta para porta 8090 (DASH) | Permanente | Médio | Não bloqueia este plano; corrigir no gest-service; registrada em `plan-e2e-integration.md §11.2` |

---

## §5 Critérios de aceite

- [ ] P11: 4 arquivos de superfície REST deletados; `ProfilePointerService` reescrito como
      colaborador real e usado por Heartbeat/Liveness; `edge.profile-pointer.read` removida do
      ACS e do catálogo pinado; `build`, `architectureCheck` e `test` PASS em EDGE **e** ACS
- [ ] P12: SharedSteps + HeartbeatLivenessSteps + ProfilePointerDriftSteps sem `PendingException`; gate test PASS com dry-run ON; E2E-005 HB-02..05 cenários passam
- [ ] P13: DeviceRegistrationEnrollmentSteps + DevicePairingSteps sem `PendingException`; `CucumberIT.java` sem dry-run; todos ~38 cenários PASS; EDGE-019 DONE 8/8; EDGE-020 DONE 5/5
- [ ] Score EDGE: 100/100 (todos os checks do BASELINE passam)
- [ ] `plan-e2e-integration.md`: E2E-005 marcado DONE

---

## §6 Dependências externas

| Dependência | Tipo | Relevância |
|---|---|---|
| `DetectionProfileFeignAdapter` + WireMock stub GEST | Feign client (mock em test) | HB-02..05 — WireMock simula GEST sem necessidade de gest-service rodando |
| Testcontainers PostgreSQL | DB de teste | Já configurado em `CucumberSpringContext`; V0700 migration aplicada via Flyway |
| `TestJwtFactory` | Test utility | Gera JWTs de device, operador e serviço com claims corretos (`deviceId`, `tenant`, `authorities`) |
| `DeviceRegistrationController.resolveTenant()` | Endpoint existente | Cobre os cenários "GEST requests tenant of device" — sem necessidade de ProfilePointerController |

---

## §7 Restrições arquiteturais (RULE-01..09)

Nenhuma regra nova. As regras existentes continuam aplicáveis:
- Steps de teste podem acessar `TestRestTemplate` (infra de teste, não produção)
- Nenhum step deve importar JPA entity ou Spring service diretamente — usar apenas HTTP via `TestRestTemplate`
- Fixtures de DB (INSERT direto) são permitidas em steps apenas via JDBC sobre o Testcontainers, nunca via JPA entity em teste de integração

---

## §8 Referências

- `docs/architecture/backend/services/edge-service/contracts/api-contract.md` — endpoints canônicos
- `docs/architecture/backend/plans/plan-e2e-integration.md §E2E-005` — cenários HB-01..05
- `docs/architecture/backend/issues/EDGE-019.md` — BDD infra (7/8 done)
- `docs/architecture/backend/issues/EDGE-020.md` — step implementation (0/5 done)
- `docs/architecture/backend/standards/testing-conventions.md` — Testcontainers singleton, Cucumber conventions
