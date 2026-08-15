# Plano -- EDGE (edge-service)

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service` · Port: 8087
> Package: `com.neuroassist.edge` · Multi-tenant: schema-per-empresa · Messaging: disabled
> Generated: 2026-07-29 · Pipeline phase: **backlog** (M4)

---

## Status das prioridades

- P1 -- ✅ DONE (scaffold + domain model + controllers skeleton) — validado 2026-08-15 BASELINE
- P2 -- ✅ IMPLEMENTADO (registration, enrollment, pairing, heartbeat, liveness) — Modo 3 LIGHT PASS
  - EDGE-005: DeviceRegistrationService.register() com enrollment token hash
  - EDGE-006: DeviceRegistrationService.enrollDevice() com credential one-time
  - EDGE-007: PairingService.pair/unpair() com exclusive pairing
  - EDGE-008: HeartbeatService.recordHeartbeat() com staleness detection
  - EDGE-009: LivenessService.evaluate() com read-time derivation
  - Gates: Lint PASS (spotlessCheck), ArchUnit PASS, test/build/e2e DEFERRED
  - Score: 81/100 (Maduro)
- P3 -- ✅ IMPLEMENTADO (JPA adapters + Flyway migration) — Modo 3 LIGHT PASS
  - EDGE-010: DispositivoEdgeRepositoryAdapter + DispositivoCredentialRepositoryAdapter + others (4 adapters)
  - EDGE-011: Flyway V0700__edge_initial_schema.sql + schema routing
  - EDGE-INT-005: MIG service integration — migration applied
  - Gates: Lint PASS (spotlessCheck), ArchUnit PASS (no-field-autowired, no-system-out, no-ddl-outside-migrations), test/build/e2e DEFERRED
  - Score: 81→82 (provisional, pending full test suite)
- P4 -- ✅ IMPLEMENTADO (Feign client real + retire + integrations GWY/REG/PAC/DASH) — Modo 3 LIGHT PASS
  - EDGE-014: DetectionProfileClient (Feign real) + DetectionProfileFeignAdapter (@Primary) com @CircuitBreaker
  - EDGE-015: DeviceRegistrationService.retire() — soft-revoke credentials + transição auditada
  - EDGE-INT-001: GestDetectionProfileContractTest (WireMock) para validar Feign contract
  - EDGE-INT-003: Rota edge-service no api-gateway application.yml configurada
  - Gates: Lint PASS (spotlessCheck — 5 files P4 code formatted), ArchUnit PASS (RULE-01..09, @FeignClient placement OK), test/build/e2e DEFERRED
  - Score: 82/100 (Maduro)
- P5 -- pendente (OAuth2 security + tenant propagation + ACS coordination)
- P6 -- pendente (domain event listeners)
- P7 -- pendente (observability + metrics + logging)
- P8 -- pendente (BDD infrastructure + full scenario coverage)
- P9 -- pendente (hardening: optimistic lock + pagination)

---

## 0. Leitura executiva

### Papel do servico (extraido de 01-architectural-vision.md)

`edge-service` e o **device authority** do NeuroAssist AI. Ele e o unico servico com autoridade sobre:
- A **identidade** de cada agente edge (`DispositivoEdge`) e seu credential OAuth2 por device
- O **pareamento exclusivo** de um device a um paciente (um device ativo por paciente, MVP)
- O **heartbeat ingestion** e a derivacao de **liveness** (`online`/`offline`) a partir de staleness — nunca de uma coluna armazenada
- A entrega do **version pointer** do perfil de deteccao (autoria de GEST) ao device via heartbeat response

O servico nunca recebe um pixel, imagem, landmark ou qualquer dado biometrico — apenas metadados de device, ponteiros de versao e uma referencia cruzada a `pacienteId` (proprietaria de PAC).

### MVP scope (extraido de 01)

- Registro de device pelo operador (conductor) com token de enrollment one-time (15min, hash-only)
- Enrollment do agente: troca de enrollment token por credential OAuth2 `client_credentials` (secret retornado uma vez)
- Pareamento exclusivo: um device `paired` por paciente; re-pairing revoga credential anterior
- Heartbeat pull-based: 30s interval, staleness threshold 90s (3 beats), DASH poll hint 15s
- Liveness derivada em read-time: pure function de `now - ultima_sincronizacao` vs. threshold
- Detection-profile version pointer: piggybacked no heartbeat response (GEST como fonte, degradado se GEST indisponivel)
- Liveness poll por DASH: `GET /devices/{id}/liveness` com ETag/304 + `serverTime` no body

### Diagnostico inicial (gaps mapeados em 06)

| # | Gap (doc 06) | Resolucao (Specs M2) |
|---|---|---|
| G1 | Mecanismo de credential do device | OAuth2 `client_credentials` por device (JWT com `deviceId+tenant`); mTLS diferido |
| G2 | Staleness threshold | 90s = 3 × 30s; configuravel via `neuroassist.edge.heartbeat.staleness-factor` |
| G3 | Intervalo de heartbeat / poll | heartbeat 30s; DASH poll hint 15s; latencia maxima ~105s |
| G4 | Bootstrap de confianca | Enrollment token one-time, TTL 15min, armazenado como SHA-256 hash |
| Open | ACS + EDGE: registro de device clients | Issue EDGE-INT-002 fecha esta coordenacao |
| Open | serverTime no LivenessView (FE dep) | EDGE-004 fecha: campo obrigatorio em todo response de liveness |

---

## 1. Estrategia geral

### Objetivo macro

Materializar `edge-service` como um microservico Spring Boot 3 / Java 21 compilavel, testavel e integravel, que implementa o contrato completo do `api-contract.md` (11 endpoints), o modelo de dados do `data-model.md` (4 tabelas, 0 coluna de status), e os invariantes de seguranca e privacidade definidos nos SPECs (nenhum dado biometrico, credential em plaintext, ou identidade derivada do body).

### Ordem das prioridades

| P | Titulo | Motivo |
|---|---|---|
| P1 | Scaffold + domain model + controllers | Base compilavel para todas as outras issues |
| P2 | Operacoes de dominio (golden path) | Registration → Enrollment → Pairing → Heartbeat → Liveness |
| P3 | Persistencia + migrations | JPA adapters + Flyway V0700 + schema routing |
| P4 | Integracoes externas + retire | Feign GEST + retire terminal + GWY/REG/PAC/DASH |
| P5 | Security + multi-tenancy | OAuth2 JWT + scope enforcement + TenantContextHolder |
| P6 | Domain events in-process | `@TransactionalEventListener` pos-commit |
| P7 | Observability | Actuator + Micrometer + structured logging |
| P8 | BDD end-to-end | Testcontainers + Cucumber ~38 cenarios |
| P9 | Hardening | Optimistic lock + paginacao + ETag |

---

## 2. Plano por prioridade

### Prioridade 1 -- Scaffold + Domain Model + Controllers Skeleton

**Status:** pendente

#### Objetivo / Resultado esperado
Modulo Gradle compilavel com estrutura hexagonal completa, agregado `DispositivoEdge` e entidades filhas com JPA, e todos os controllers / DTOs com endpoints mapeados (sem implementacao de negocio ainda).

#### Issues vinculadas
- EDGE-001 (Gradle module + Spring Boot entrypoint)
- EDGE-002 (DispositivoEdge aggregate + PairingState enum)
- EDGE-003 (DispositivoEnrollment + DispositivoCredential + DispositivoEdgeTransition + ports)
- EDGE-004 (REST controllers skeleton + DTOs + GlobalExceptionHandler)

#### Specs cobertas
- SPEC-01 (api-surface), SPEC-03 (pairing), SPEC-04 (data-model)

#### Sequencia de execucao
- Fase 1: EDGE-001 (build compilavel)
- Fase 2: EDGE-002 + EDGE-003 em paralelo (domain model)
- Fase 3: EDGE-004 (controllers sobre o modelo)

#### Criterios de aceite
- [ ] `./gradlew :services:edge-service:compileJava` sai com exit 0
- [ ] 11 endpoints mapeados (api-contract §9) — todos retornam 501 ou UnsupportedOperationException
- [ ] `DispositivoEdge` nao tem coluna `status`
- [ ] `LivenessView` tem campo `serverTime` (M3 warning fechado)

#### Riscos
- Conflito de versao de dependencias Spring Cloud vs Spring Boot 3.x — fixar BOM
- `@Check` JPA pode nao gerar DDL correto sem hibernate.ddl-auto=validate; validar contra Flyway

#### Instrucao para implementador
Iniciar pelo EDGE-001 (Gradle module). Usar o `generator-config.yml` como referencia de dependencias. Para EDGE-002, nao adicionar coluna `status` — e derivada. Para EDGE-004, garantir que `POST /enroll` esta marcado como `permitAll` no SecurityConfig (sem Bearer) mesmo nesta fase de skeleton.

---

### Prioridade 2 -- Operacoes de Dominio (Golden Path)

**Status:** ✅ IMPLEMENTADO (2026-08-15 17:09-17:30 UTC)

#### Objetivo / Resultado esperado
Fluxo completo de: registro de device (201 + enrollment token) → enrollment (200 + credential) → pairing (201 paired) → heartbeat (200 + liveness + profile pointer) → liveness poll (200 status derivado). Todos os invariantes de seguranca aplicados (token hash-only, credential hash-only, deviceId do token nunca do body).

#### Issues vinculadas
- EDGE-005 (DeviceRegistrationService.register — enrollment token)
- EDGE-006 (DeviceRegistrationService.enroll — credential one-time)
- EDGE-007 (PairingService.pair + unpair)
- EDGE-008 (HeartbeatService.recordHeartbeat)
- EDGE-009 (LivenessService.evaluate + liveness endpoints)

#### Specs cobertas
- SPEC-02, SPEC-03, SPEC-10, SPEC-11

#### Sequencia de execucao
- Fase 1: EDGE-005 (register → token hash)
- Fase 2: EDGE-006 (enroll → credential)
- Fase 3: EDGE-007 (pair + unpair)
- Fase 4: EDGE-008 (heartbeat — depende de GEST mock via DetectionProfilePort)
- Fase 5: EDGE-009 (liveness — depende de EDGE-008 para detectar cruzamentos)

#### Criterios de aceite
- [ ] Fluxo completo register → enroll → pair → heartbeat → liveness executa sem erro em teste de integracao
- [ ] `clientSecret` nunca em banco (apenas `secret_hash`)
- [ ] `ultima_sincronizacao` carimbado pelo servidor, nunca pelo cliente
- [ ] Cruzamento online→offline gera exatamente uma `liveness` transition row

#### Riscos
- EDGE-008 depende de `DetectionProfilePort` (GEST via Feign) — usar `Optional.empty()` stub para P2; Feign real vira em P4
- Liveness crossing em EDGE-009 pode duplicar audit rows se chamada simultaneamente — checar idempotencia

#### Instrucao para implementador
Para P2, usar `DetectionProfilePort` com implementacao stub (`Optional.empty()`) — a implementacao Feign real vem em P4 (EDGE-014). Todos os campos de deviceId devem ser extraidos do SecurityContext, nunca do body. Usar `@Version` para `row_version` desde P2 — optimistic lock sera testado em P9.

---

### Prioridade 3 -- Persistencia + Migrations

**Status:** pendente

#### Objetivo / Resultado esperado
Quatro adapters JPA implementando as interfaces de porta do dominio, com schema routing por tenant (`t_<tenantId>`). Migration V0700 contribuida ao MIG service e aplicavel sem erros.

#### Issues vinculadas
- EDGE-010 (JPA adapters para todos os 4 ports)
- EDGE-011 (Flyway V0700 + contribuicao ao MIG)
- EDGE-INT-005 (integracao com MIG service)

#### Specs cobertas
- SPEC-04

#### Sequencia de execucao
- Fase 1: EDGE-010 (adapters JPA)
- Fase 2: EDGE-011 (gerar Flyway SQL a partir de data-model.md)
- Fase 3: EDGE-INT-005 (copiar para MIG + verificar aplicacao)

#### Criterios de aceite
- [ ] CRUD em `dispositivo_edge` funciona com tenant isolado
- [ ] `ux_dispositivo_credential_active` impede dois credentials ativos para o mesmo device
- [ ] Migration V0700 aplica em PostgreSQL 16 sem erros

#### Riscos
- `be-migration-generator.py` pode nao extrair corretamente os blocos SQL do `data-model.md` — verificar output do script antes de usar
- Schema routing pode vazar entre requests em testes paralelos — usar thread-local e garantir clear no finally

#### Instrucao para implementador
Rodar `python scripts/be-migration-generator.py --service edge-service` e revisar o SQL gerado antes de usar. A ordem de criacao das tabelas deve respeitar FKs: `dispositivo_edge` primeiro, depois as tres filhas. Incluir o bloco pgcrypto no schema global (nao empresa).

---

### Prioridade 4 -- Integracoes Externas + Retire

**Status:** pendente

#### Objetivo / Resultado esperado
Feign client para GEST com circuit breaker e fallback degradado. Operacao de retire terminal com revocacao de credential. Integracoes com GWY (rota), REG (discovery) e PAC/DASH (endpoints servidos).

#### Issues vinculadas
- EDGE-014 (DetectionProfileClient Feign + Resilience4j)
- EDGE-015 (DeviceRegistrationService.retire)
- EDGE-INT-001 (integracao GEST + WireMock testes)
- EDGE-INT-003 (integracao GWY — rota edge/**)
- EDGE-INT-004 (integracao REG — Eureka)
- EDGE-INT-006 (integracao PAC — EdgeDevicePort)
- EDGE-INT-007 (integracao DASH — liveness poll + serverTime)

#### Specs cobertas
- SPEC-10, SPEC-03, integration-contracts.md

#### Sequencia de execucao
- Fase 1: EDGE-014 (Feign GEST — substitui stub de P2); EDGE-015 (retire) em paralelo
- Fase 2: EDGE-INT-001 (testes WireMock GEST)
- Fase 3: EDGE-INT-003, EDGE-INT-004 (GWY + REG config)
- Fase 4: EDGE-INT-006, EDGE-INT-007 (PAC + DASH)

#### Criterios de aceite
- [ ] GEST indisponivel: heartbeat aceito com `profilePointerDegraded=true`
- [ ] device retired nao aceita heartbeat (409 `device-retired`)
- [ ] GWY roteia `/api/v1/edge/**` corretamente; POST /enroll sem Bearer
- [ ] GET /patients/{id}/device retorna 404 para cross-tenant (nao 403)
- [ ] `serverTime` presente em toda resposta de liveness (EDGE-INT-007 fecha M3 warning)

#### Riscos
- Circuit breaker Resilience4j pode interferir com WireMock em testes (timeout = circuit open) — configurar threshold alto em `application-test.yml`
- GWY precisa de alteracao no modulo separado (neuroassist-ai-api-gateway) — garantir que o implementador tem acesso ao modulo

#### Instrucao para implementador
Para EDGE-014, configurar `neuroassist.gest.url` apontando para WireMock em testes (`application-test.yml`) e para `lb://gest-service` em producao. Circuit breaker threshold alto em testes (ex: 100% / 100-call) para evitar falsos positivos. Para EDGE-INT-007: `serverTime` e mandatory em todo response de liveness — nao optional.

---

### Prioridade 5 -- Security + Multi-tenancy

**Status:** pendente

#### Objetivo / Resultado esperado
OAuth2 JWT resource server configurado com scope enforcement por endpoint. TenantContextHolder propagado para SchemaRoutingDataSource. X-Correlation-Id gerado/ecoado. Coordenacao com ACS sobre registro de device clients documentada.

#### Issues vinculadas
- EDGE-012 (SecurityConfig + jwk-set-uri + scope enforcement)
- EDGE-013 (TenantContextHolder + MDC + CorrelationIdFilter)
- EDGE-INT-002 (coordenacao ACS — device client registration)

#### Specs cobertas
- SPEC-06, SPEC-07, hub-integration.md

#### Sequencia de execucao
- Fase 1: EDGE-012 (SecurityConfig)
- Fase 2: EDGE-013 (TenantContextHolder + filters)
- Fase 3: EDGE-INT-002 (documentar estrategia ACS)

#### Criterios de aceite
- [ ] 401 em endpoint protegido sem token; 403 com scope errado
- [ ] POST /enroll aceito sem Bearer
- [ ] `tenantId` derivado do JWT, nunca do body (testado com body spoof)
- [ ] TenantContextHolder.clear() no finally (sem leak entre requests)
- [ ] Estrategia ACS documentada em hub-integration.md §4.2

#### Riscos
- `jwk-set-uri` aponta para ACS via REG — se REG estiver fora no startup, jwk cache falha; configurar fallback ou pre-carregar JWKS
- Leak de ThreadLocal em ambiente com pool de threads — garantir clear no finally do filter

#### Instrucao para implementador
Para EDGE-INT-002: a estrategia mais simples para MVP e EDGE registrar o device client no ACS via endpoint de administracao do ACS ao emitir o credential (EDGE-006). Alternativa: ACS delega validacao de `client_credentials` desconhecidos para EDGE. Documentar a escolha e confirmar com a equipe ACS antes de implementar.

---

### Prioridade 6 -- Domain Events In-Process

**Status:** pendente

#### Objetivo / Resultado esperado
Sete eventos de dominio (DispositivoRegistrado, Pareado, Despareado, HeartbeatRecebido, FicouOffline, VoltouOnline, ProfileDriftDetected) publicados pos-commit via Spring ApplicationEventPublisher. Listeners com `@TransactionalEventListener(phase=AFTER_COMMIT)` — falha no listener nao propaga para a transacao.

#### Issues vinculadas
- EDGE-016 (TransactionalEventListener + 7 domain event classes)

#### Specs cobertas
- SPEC-09

#### Sequencia de execucao
- Fase 1: criar classes de evento em `domain/event/`
- Fase 2: criar listeners e verificar AFTER_COMMIT behavior

#### Criterios de aceite
- [ ] Rollback nao aciona listeners
- [ ] Falha no listener nao afeta a transacao original
- [ ] Todos os 7 eventos tem payload com `deviceId`, `tenantRef`, `at`

#### Riscos
- Event ordering: `DispositivoFicouOffline` e derivado em read-time, nao em write — o listener deve ser `DeviceAuditEventListener` que apenas loga/emite metrica (nao tenta "fixar" o estado)
- `DispositivoFicouOffline` nao deve ser emitido a partir de um scheduler no MVP

#### Instrucao para implementador
Os eventos de dominio sao in-process apenas (sem broker). Nao adicionar `@Async` nos listeners a menos que seja necessario para evitar `StaleObjectStateException` — se necessario, usar try-catch em vez de async para manter simplicidade. Para `ProfileDriftDetected`: emitir apenas quando drift mudar (nao em todo heartbeat).

---

### Prioridade 7 -- Observabilidade

**Status:** pendente

#### Objetivo / Resultado esperado
Actuator health/readiness/liveness + Micrometer contadores de negocio + logging estruturado JSON com MDC. Zero credenciais em logs (verificado por teste de privacidade).

#### Issues vinculadas
- EDGE-017 (Actuator + Micrometer EdgeMetrics)
- EDGE-018 (structured JSON logging + MDC + privacy test)

#### Specs cobertas
- SPEC-07

#### Sequencia de execucao
- Fase 1: EDGE-017 (Actuator + metricas)
- Fase 2: EDGE-018 (logging JSON + teste de privacidade)

#### Criterios de aceite
- [ ] `GET /actuator/health` retorna UP
- [ ] Contadores `edge.heartbeats.received`, `edge.pairing.transitions`, `edge.profile.drift.detected` disponibilizados
- [ ] Teste de privacidade passa: grep em logs de enroll nao encontra `clientSecret`, `tokenHash`, `enrollmentToken`
- [ ] `deviceId` presente nos logs de heartbeat

#### Riscos
- Nenhum risco critico; assegurar que o JsonLayout nao inclui fields de request/response body completos (apenas MDC)

#### Instrucao para implementador
Para EDGE-018, adicionar um `PatternLayout` ou `MaskingConverter` que intercepte strings com patterns `/cs_[a-z0-9]+/` e `/et_[a-z0-9]+/` e as mascare antes do log. O teste de privacidade e: chamar POST /enroll, capturar log, assercar que nenhum valor de `clientSecret` ou `enrollmentToken` aparece no output.

---

### Prioridade 8 -- BDD End-to-End

**Status:** pendente

#### Objetivo / Resultado esperado
Infraestrutura de teste Cucumber + Testcontainers funcionando para os 4 arquivos .feature do M3. Todos os ~38 cenarios passando (gate BDD verde). WireMock stubbing GEST integrado.

#### Issues vinculadas
- EDGE-019 (infra BDD: Testcontainers + SecurityConfig test + WireMock)
- EDGE-020 (implementacao completa de todos os ~38 cenarios)

#### Specs cobertas
- SPEC-08

#### Sequencia de execucao
- Fase 1: EDGE-019 (scaffold e infra de teste)
- Fase 2: EDGE-020 (step definitions completas)

#### Criterios de aceite
- [ ] `./gradlew :services:edge-service:test -Dcucumber.filter.tags=@integration` exit 0
- [ ] Nenhum cenario Pending ou Undefined
- [ ] Cenario de GEST degraded: heartbeat retorna `profilePointerDegraded=true`
- [ ] Cenario de ETag 304: liveness poll com If-None-Match retorna 304

#### Riscos
- Testcontainers pode conflitar com PostgreSQL local na porta padrao — usar porta aleatoria em `application-test.yml`
- SecurityConfig de teste precisa aceitar JWT de teste sem validar assinatura real — usar `@MockBean JwtDecoder` ou `spring.security.oauth2.resourceserver.jwt.public-key-location`

#### Instrucao para implementador
Para EDGE-019: usar `TestJwtFactory` (criar classe utilitaria) para emitir JWTs de teste com qualquer scope/claims — nao usar tokens reais do ACS. Para EDGE-020: os 4 StepDef files ja existem do M3 scaffold (apenas esqueletos com pending) — preencher as implementacoes com chamadas REST via `RestTemplate` ou `WebTestClient` ao servico iniciado pelo Testcontainers.

---

### Prioridade 9 -- Hardening

**Status:** pendente

#### Objetivo / Resultado esperado
Optimistic lock via `row_version` com 409 em write concorrente. Endpoint GET /devices/{id} com status/drift derivados. Listagem paginada por tenant.

#### Issues vinculadas
- EDGE-021 (optimistic-lock + GET /devices/{id} + pagination)

#### Specs cobertas
- SPEC-05, SPEC-01

#### Sequencia de execucao
- Fase 1: EDGE-021 (verificar @Version mapeado + GlobalExceptionHandler para OptimisticLockException + GET /devices/{id} + listagem paginada)

#### Criterios de aceite
- [ ] Write concorrente com `row_version` stale retorna 409 `optimistic-lock`
- [ ] GET /devices/{id} deriva status em read-time (nao coluna armazenada)
- [ ] Listagem paginada filtra por tenant do JWT (nao vaza devices de outros tenants)

#### Riscos
- Teste de concorrencia pode ser flakey em CI — usar `CountDownLatch` para sincronizar as duas threads

#### Instrucao para implementador
`OptimisticLockException` do JPA deve ser capturada no `GlobalExceptionHandler` e convertida para 409 `optimistic-lock` (ProblemDetail) com `correlationId`. Para GET /devices/{id}: chamar `LivenessService.evaluate()` e `DetectionProfilePort.currentPointerForTenant()` em read-time — nao ler coluna `status` do banco.

---

## 3. Dependencias externas

| Servico | Tipo | Issues dependentes | Criticidade |
|---|---|---|---|
| ACS (auth) | OAuth2 JWK endpoint + device token issuance | EDGE-012, EDGE-INT-002 | Critica — sem ACS, nenhum endpoint protegido funciona |
| GEST (detection profile) | Feign GET /detection-profiles/active | EDGE-014, EDGE-INT-001 | Alta — degraded fallback cobre ausencia |
| REG (Eureka) | Service discovery | EDGE-001, EDGE-INT-004 | Alta — GWY e Feign dependem do REG |
| GWY (routing) | Route /api/v1/edge/** | EDGE-INT-003 | Alta — agente edge nao acessa EDGE diretamente |
| MIG (migrations) | V0700 DDL | EDGE-011, EDGE-INT-005 | Critica — schema deve existir antes do primeiro request |
| PAC | Consome EdgeDevicePort | EDGE-INT-006 | Media — PAC valida dispositivo_edge_id via EDGE |
| DASH | Consome liveness poll | EDGE-INT-007 | Media — DASH depende de serverTime para derivar age |

---

## 4. Restricoes arquiteturais

- **ArchUnit RULE-01..08 (CLAUDE.md):** camadas hexagonais (api/application/domain/infrastructure) sem violacao de dependencia
- **@Transactional somente em `application.service`:** nunca em controllers, nunca em adapters de infraestrutura diretamente
- **Nenhuma coluna `status` em `dispositivo_edge`:** liveness e derivada em read-time (ADR-006 — violacao e regressao de seguranca)
- **Nenhum dado em plaintext de credential:** `tokenHash`, `secretHash` — nunca plaintext em banco, log ou response (exceto o momento de emissao)
- **`deviceId` do token, nunca do body:** anti-spoofing invariante #2 (ADR-005)
- **Append-only para `dispositivo_edge_transition`:** nenhum UPDATE/DELETE — violacao e perda de auditoria
- **Soft-retire:** nunca hard-delete `dispositivo_edge` — mantido por referencia historica de `Evento`/`Comando`
- **Tenant derivado do JWT exclusivamente:** nunca de body, path, ou query string (schema-per-empresa)

---

## 5. Referencias

- `docs/architecture/backend/services/edge-service/contracts/api-contract.md`
- `docs/architecture/backend/services/edge-service/contracts/data-model.md`
- `docs/architecture/backend/services/edge-service/contracts/event-contracts.md`
- `docs/architecture/backend/services/edge-service/contracts/hub-integration.md`
- `docs/architecture/backend/services/edge-service/contracts/integration-contracts.md`
- `docs/architecture/backend/services/edge-service/spec/backend/SPEC-01..11`
- `docs/architecture/backend/services/edge-service/edge-service-spec.md`
- `docs/architecture/backend/services/edge-service/generator-config.yml`
- `docs/architecture/backend/services/edge-service/setup-local.md`
- `docs/architecture/backend/services/edge-service/adr/ADR-005-device-pairing-identity-model.md`
- `docs/architecture/backend/services/edge-service/adr/ADR-006-heartbeat-liveness-profile-delivery.md`
- `docs/architecture/backend/issues/issues_map-EDGE.md`
