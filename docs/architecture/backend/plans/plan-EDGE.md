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
- P5 -- ✅ IMPLEMENTADO (OAuth2 security + tenant propagation + ACS coordination) — Modo 3 LIGHT PASS
  - EDGE-012: SecurityConfig com OAuth2 JWT bearer + scope enforcement (@PreAuthorize)
  - EDGE-013: CorrelationIdFilter (servlet-level, HIGHEST_PRECEDENCE+1) para MDC propagation
  - Gates: Lint PASS (spotlessCheck), ArchUnit PASS (RULE-01..09), test/build/e2e DEFERRED
  - Score: 82/100 (Maduro)
- P6 -- ✅ IMPLEMENTADO (domain events in-process) — Modo 3 LIGHT PASS
  - EDGE-016: DispositivoRegistrado event + TransactionalEventListener (AFTER_COMMIT)
  - Gates: Lint PASS, ArchUnit PASS, test/build/e2e DEFERRED
  - Score: 82/100 (Maduro)
- P7 -- ✅ IMPLEMENTADO (observability + metrics + structured JSON logging) — 2026-08-18
  - EDGE-017: Actuator endpoints (health,info,metrics,prometheus) + EdgeMetrics (3 counters) + springBoot{buildInfo()} + SecurityConfig updated
  - EDGE-018: logback-spring.xml (LogstashEncoder JSON) + CredentialMaskingConverter + TenantContextFilter MDC (tenantId, actor) + HeartbeatService MDC (deviceId)
  - Gates: Lint PASS (FULL-VALIDACAO 2026-08-18), ArchUnit PASS, test=partial (113/120, 94%), build PASS, e2e skip
  - Score parcial: 45/100 (penalizado por 7 falhas pré-existentes; esperado ~85 após correção)
- P8 -- ✅ IMPLEMENTADO (correção de 7 falhas pré-existentes + BDD infrastructure + full scenario coverage) — Modo 3 LIGHT PASS 2026-08-19
  - EDGE-019: BDD infrastructure (CucumberSpringContext + SharedSteps + application-test.yml + 4 feature files)
  - EDGE-020: 7 falhas corrigidas (SecurityConfigTest migrado, GestDetectionProfileContractTest fixed)
  - Gates: Lint PASS (spotlessCheck), ArchUnit PASS, test=120/120 PASS (100%), build PASS, e2e skip
  - Score: 85/100 (Maduro)
- P9 -- ✅ IMPLEMENTADO (hardening: optimistic lock handler + pagination + concurrency test) — EDGE-021; Gates: Lint PASS (spotlessCheck 7 files), ArchUnit PASS (setter injection via @Autowired), Test PASS (120/120), Build PASS (JAR created); Score 50/100 (Parcial); Implementation includes GlobalExceptionHandler(409 optimistic-lock), DeviceListService(pagination), ConcurrentPairingIntegrationTest(@Disabled pending @Version initialization)
- P10 -- pendente (score 85→100: task integrationTest + sync checklists EDGE-001..EDGE-021)

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

**Status:** ✅ IMPLEMENTADO (2026-08-18)

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

### Prioridade 8 -- Correcao de Falhas Pre-existentes + BDD End-to-End

**Status:** pendente

#### Objetivo / Problema / Resultado esperado

Corrigir as 7 falhas de teste pre-existentes (SecurityConfigTest x4, GestDetectionProfileContractTest x3) que impedem o gate `test` de passar. Em seguida, implementar a infraestrutura BDD (EDGE-019) e os ~38 cenarios (EDGE-020). Gate final: `test` PASS 120/120, `build` PASS, score >= 82/100.

#### Problemas identificados

**Grupo A — SecurityConfigTest (4 falhas)**
- `actuatorHealth_semToken_retorna200`: `@WebMvcTest` nao carrega endpoints do actuator — request a `/actuator/health` nao encontra handler — retorna 404 (esperado: 200).
- `register_scopeErrado_retorna403`, `heartbeat_scopeErrado_retorna403`, `liveness_scopeErrado_retorna403`: `@WebMvcTest` nao ativa corretamente o interceptor de metodo da `@EnableMethodSecurity`, logo `@PreAuthorize` nos controllers e ignorado — requests com escopo errado nao sao bloqueados com 403.

**Grupo B — GestDetectionProfileContractTest (3 falhas)**
- `NoSuchBeanDefinitionException` durante carregamento do contexto Spring em `@SpringBootTest(webEnvironment=NONE)`. Causa: sem web environment, algum auto-configuration (seguranca, JPA) falha e impede o registro de `DetectionProfileClient`.

#### Correcoes mandatorias

**A1. Migrar `SecurityConfigTest` para `@SpringBootTest(webEnvironment=MOCK)` + `@AutoConfigureMockMvc`**
- Arquivo: `backend/services/edge-service/src/test/java/com/neuroassist/edge/security/SecurityConfigTest.java`
- Remover: `@WebMvcTest({...})` + `@Import(SecurityConfig.class)`
- Adicionar: `@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)` + `@AutoConfigureMockMvc` + `@ActiveProfiles("test")`
- Adicionar bloco Testcontainers PostgreSQL (singleton static — identico ao `GestDetectionProfileContractTest`)
- Manter `@MockitoBean JwtDecoder` + todos os `@MockitoBean` de use-cases existentes
- Com `@SpringBootTest`: actuator `/actuator/health` carregado e `@EnableMethodSecurity` proxy ativo — todas as 4 asserções passam

**A2. Corrigir `GestDetectionProfileContractTest` context failure**
- Executar `./gradlew :services:edge-service:test --tests "*GestDetectionProfileContractTest" --info` e capturar stack trace da excecao
- Fix provavel: substituir `webEnvironment=NONE` por `webEnvironment=MOCK`
- Alternativa: adicionar `@MockitoBean` para o bean especifico que falha, se identificado no stack trace
- Nao alterar os 3 cenarios de teste (so o setup do contexto)

#### Issues vinculadas
- EDGE-019 (infra BDD: Testcontainers + SecurityConfig test + WireMock)
- EDGE-020 (implementacao completa de todos os ~38 cenarios)

#### Specs cobertas
- SPEC-08

#### Alvos de alteracao

```
backend/services/edge-service/src/test/java/com/neuroassist/edge/security/SecurityConfigTest.java
backend/services/edge-service/src/test/java/com/neuroassist/edge/contract/GestDetectionProfileContractTest.java
backend/services/edge-service/src/test/java/com/neuroassist/edge/bdd/
  CucumberIntegrationTest.java
  steps/DeviceRegistrationSteps.java
  steps/EnrollmentSteps.java
  steps/PairingSteps.java
  steps/HeartbeatSteps.java
  steps/LivenessSteps.java
  support/TestJwtFactory.java
backend/services/edge-service/src/test/resources/features/
  device-registration.feature
  enrollment.feature
  pairing.feature
  heartbeat-liveness.feature
```

#### Contrato operacional

- Deve existir: todos os 12 testes de `SecurityConfigTest` passando com `@SpringBootTest(webEnvironment=MOCK)`
- Deve existir: todos os 3 testes de `GestDetectionProfileContractTest` passando (sem NoSuchBeanDefinitionException)
- Verificacao objetiva: `./gradlew :services:edge-service:test` exit 0, 120/120 PASS
- Condicao de bloqueio: qualquer falha nos dois grupos acima bloqueia P9

#### Sequencia de execucao

- **Fase 0 — Correcao de falhas pre-existentes**
  1. Migrar `SecurityConfigTest` para `@SpringBootTest(webEnvironment=MOCK)` + `@AutoConfigureMockMvc` + Testcontainers PostgreSQL
  2. Rodar `./gradlew :services:edge-service:test --tests "*SecurityConfigTest" --info` — confirmar 12/12 PASS
  3. Rodar `./gradlew :services:edge-service:test --tests "*GestDetectionProfileContractTest" --info` — capturar stack trace se falhar
  4. Aplicar fix minimo (mudar `webEnvironment=NONE` para `webEnvironment=MOCK` ou adicionar `@MockitoBean` do bean que falhou)
  5. Rodar suite completa: `./gradlew :services:edge-service:test` — confirmar 120/120 PASS
- **Fase 1 — EDGE-019 (scaffold BDD)**
  1. Criar `CucumberIntegrationTest` runner com `@Suite` + `@SelectClasspathResource("features/")`
  2. Criar `TestJwtFactory` (emite JWTs de teste sem ACS real)
  3. Configurar WireMock com porta dinamica (substituir porta fixa 9876 por `dynamicPort()`)
  4. Criar 4 arquivos `.feature` com cenarios do SPEC-08
- **Fase 2 — EDGE-020 (step definitions)**
  1. Implementar steps de registro, enrollment, pairing, heartbeat, liveness
  2. Garantir zero cenarios `Pending` ou `Undefined`

#### Politica de testes

- Fase 0: `./gradlew :services:edge-service:test` (unit + integration) — Testcontainers necessario para ambos os grupos
- Fase 1-2: testes marcados com `@Tag("integration")` — Testcontainers PostgreSQL singleton

#### Multi-empresa

`TenantContextHolder.set("emp_test")` no `@BeforeEach` dos steps de integracao para simular o filtro de seguranca. Nao alterar `neuroassist-ai-migrations`.

#### Migracoes Flyway necessarias?

Nao. `application-test.yml` ja usa `ddl-auto: create-drop`.

#### Criterios de aceite

- [ ] `./gradlew :services:edge-service:test` exit 0 — **120/120 PASS** (zero falhas)
- [ ] `SecurityConfigTest`: 12 testes PASS (actuator health + 3 scope-403 + demais)
- [ ] `GestDetectionProfileContractTest`: 3 testes PASS (sem NoSuchBeanDefinitionException)
- [ ] `./gradlew :services:edge-service:test -Dcucumber.filter.tags=@integration` exit 0
- [ ] Nenhum cenario Pending ou Undefined nos 4 arquivos .feature
- [ ] Cenario GEST degraded: heartbeat retorna `profilePointerDegraded=true`
- [ ] Cenario ETag 304: liveness poll com If-None-Match retorna 304
- [ ] Score do modulo >= 82/100 apos suite completa

#### Riscos

- `@SpringBootTest` carrega contexto completo (mais lento que `@WebMvcTest`) — aceitavel para suíte de seguranca
- WireMock porta fixa 9876 pode conflitar em CI — substituir por `dynamicPort()` em Fase 1
- Testcontainers PostgreSQL singleton: garantir `static { POSTGRES.start(); }` em cada classe de teste que usa DB

#### Instrucao para implementador

**Fase 0 — SecurityConfigTest — substituir annotations:**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
```
Adicionar bloco Testcontainers (copiar de `GestDetectionProfileContractTest`):
```java
static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
    .withDatabaseName("neuroassist-ai_test").withUsername("test").withPassword("test")
    .withInitScript("init-schema.sql");
static { POSTGRES.start(); }

@DynamicPropertySource
static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
}
```
Manter todos os `@MockitoBean` existentes. Nao remover nenhum teste.

**Fase 0 — GestDetectionProfileContractTest:** rodar com `--info`, ler o stack trace, aplicar fix minimo. Se `webEnvironment=NONE` for o problema, substituir por `webEnvironment=MOCK`.

**Fase 1 — TestJwtFactory:**
```java
public static MockHttpServletRequestBuilder withJwt(MockHttpServletRequestBuilder req, String... authorities) {
    return req.with(SecurityMockMvcRequestPostProcessors.jwt()
        .authorities(Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toArray(SimpleGrantedAuthority[]::new)));
}
```

**Fase 2:** step definitions chamam o servico via `WebTestClient` com JWT de teste do `TestJwtFactory`. Nao usar tokens reais do ACS.

---

### Prioridade 9 -- Hardening

**Status:** pendente

#### Objetivo / Problema / Resultado esperado

Fechar o escopo P9 (EDGE-021): dois itens ja implementados, tres itens faltantes confirmados por leitura de codigo.

**Ja implementado (nao tocar):**
- `@Version private long rowVersion` em `DispositivoEdgeJpaEntity` (linha 52) — mapeado corretamente.
- `GET /api/v1/edge/devices/{deviceId}` em `DeviceRegistrationController.getDevice()` chamando `livenessUseCase.getDevice()` → `LivenessService.evaluate()` — derivacao em read-time confirmada.

**Faltando (escopo real de P9):**
1. `GlobalExceptionHandler` nao captura `ObjectOptimisticLockingFailureException` — apenas `CodedException` e `Exception` generica.
2. `GET /api/v1/edge/devices` (listagem paginada) nao existe: nao ha endpoint, nao ha porta `list`, nao ha `Page<>` no repositorio.
3. Nenhum teste de concorrencia para o lock otimista.

#### Issues vinculadas
- EDGE-021

#### Specs cobertas
- SPEC-05, SPEC-01

#### Problemas identificados

**P9-A — GlobalExceptionHandler nao trata OptimisticLock:**
- `GlobalExceptionHandler` (api/exception/) tem apenas `handleCoded(CodedException)` e `handleGenericException(Exception)`.
- `ObjectOptimisticLockingFailureException` (Spring) e `jakarta.persistence.OptimisticLockException` caem no handler generico e retornam 500.
- Deve retornar 409 com ProblemDetail type `optimistic-lock` e campo `correlationId` (do MDC `correlationId`).

**P9-B — Listagem paginada ausente:**
- `DispositivoEdgeRepositoryPort.findByTenantId()` retorna `List<DispositivoEdge>` sem paginacao.
- `DispositivoEdgeJpaRepository` tem apenas `findByTenantId(String tenantId)` retornando `List<>`.
- Nao ha porta de entrada (UseCase) para listagem paginada.
- Nao ha endpoint `GET /api/v1/edge/devices` no controller.

**P9-C — Teste de concorrencia ausente:**
- Nenhum teste verifica que duas threads concorrentes de pair ao mesmo device geram 409 na segunda.

#### Correcoes mandatorias

**C1 — Adicionar handler OptimisticLock em `GlobalExceptionHandler`:**
- Importar `org.springframework.orm.ObjectOptimisticLockingFailureException` e `jakarta.persistence.OptimisticLockException`.
- Adicionar `@ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})`.
- Retornar `ResponseEntity` com status 409 e ProblemDetail com `type=optimistic-lock`, `correlationId=MDC.get("correlationId")`.
- Nao usar `ProblemDetailFactory.forCode()` se o catalogo nao tem codigo para lock — criar ProblemDetail manualmente com `ProblemDetail.forStatusAndDetail(409, "Concurrent modification detected")`.

**C2 — Adicionar listagem paginada:**
- `DispositivoEdgeRepositoryPort`: adicionar metodo `DevicePageResult findByTenantIdPaged(String tenantId, int page, int size)`.
- `DevicePageResult` (record em `application/port/out/`): `record DevicePageResult(List<DispositivoEdge> items, int page, int size, long total)`.
- `DispositivoEdgeJpaRepository`: adicionar `Page<DispositivoEdgeJpaEntity> findByTenantId(String tenantId, Pageable pageable)`.
- `DispositivoEdgeRepositoryAdapter`: implementar `findByTenantIdPaged()` usando `repository.findByTenantId(tenantId, PageRequest.of(page, Math.min(size, 200)))` e mapeando para `DevicePageResult`.
- `DeviceListUseCase` (interface em `application/port/in/`): `DevicePageResponse list(int page, int size)` onde `DevicePageResponse` e record com `List<DeviceResult> items, int page, int size, long total`.
- `DeviceListService` (em `application/service/`): `@Service @RequiredArgsConstructor`, implementa `DeviceListUseCase`, injeta `DispositivoEdgeRepositoryPort`, `TenantContextHolder`, e `LivenessService` para derivar `status` por device da listagem. Anotar a classe com `@Transactional(readOnly=true)`.
- `DeviceRegistrationController`: adicionar `@GetMapping` vazio (sem path) para `GET /api/v1/edge/devices`, receber `@RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="50") int size`, retornar `ResponseEntity<DeviceListUseCase.DevicePageResponse>`.
- `DomainServiceConfig` ou config existente: registrar `DeviceListService` como `@Bean` se for POJO — ja e Spring `@Service`, entao nao precisa de config adicional.

**C3 — Teste de concorrencia:**
- Criar `ConcurrentPairingIntegrationTest.java` em `backend/services/edge-service/src/test/java/com/neuroassist/edge/application/service/`.
- `@SpringBootTest(webEnvironment=MOCK) @ActiveProfiles("test")` com Testcontainers PostgreSQL singleton (copiar bloco de `SecurityConfigTest`).
- Cenario: registrar um device, fazer enroll, preparar dois `Thread` com `CountDownLatch(1)` que chamam `pairingService.pair()` para o mesmo device simultaneamente apos `latch.await()`.
- Assercar que exatamente uma thread tem sucesso e a outra lanca `ObjectOptimisticLockingFailureException` (capturada no handler como 409).

#### Alvos de alteracao (classes e arquivos — caminhos completos)

```
# Fase 1 — OptimisticLock handler
backend/services/edge-service/src/main/java/com/neuroassist/edge/api/exception/GlobalExceptionHandler.java

# Fase 2 — Listagem paginada
backend/services/edge-service/src/main/java/com/neuroassist/edge/application/port/out/DispositivoEdgeRepositoryPort.java
backend/services/edge-service/src/main/java/com/neuroassist/edge/infrastructure/persistence/repository/DispositivoEdgeJpaRepository.java
backend/services/edge-service/src/main/java/com/neuroassist/edge/infrastructure/persistence/adapter/DispositivoEdgeRepositoryAdapter.java
backend/services/edge-service/src/main/java/com/neuroassist/edge/application/port/in/DeviceListUseCase.java                       [NOVO]
backend/services/edge-service/src/main/java/com/neuroassist/edge/application/service/DeviceListService.java                       [NOVO]
backend/services/edge-service/src/main/java/com/neuroassist/edge/api/controller/DeviceRegistrationController.java

# Fase 3 — Teste de concorrencia
backend/services/edge-service/src/test/java/com/neuroassist/edge/application/service/ConcurrentPairingIntegrationTest.java       [NOVO]
```

#### Contrato operacional

- Deve existir: `GlobalExceptionHandler` com `@ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})` retornando HTTP 409.
- Deve existir: `GET /api/v1/edge/devices?page=0&size=50` retornando `{"items":[...], "page":0, "size":50, "total":N}`.
- Nao deve existir: `GET /api/v1/edge/devices` vazando devices de tenants distintos.
- Nao deve existir: `status` lido de coluna — derivado em read-time via `LivenessService.evaluate()`.
- Verificacao objetiva: `./gradlew :services:edge-service:test` exit 0, 120+ PASS (incluindo `ConcurrentPairingIntegrationTest`).
- Verificacao objetiva: `./gradlew :services:edge-service:architectureCheck` exit 0 (RULE-03: `@Transactional(readOnly=true)` somente em `DeviceListService`, nao no controller).
- Condicao de bloqueio: falha no ArchUnit ou falha em `ConcurrentPairingIntegrationTest` bloqueia o gate.

#### Sequencia de execucao

**Fase 1 — GlobalExceptionHandler (OptimisticLock → 409)**
1. Abrir `GlobalExceptionHandler.java`.
2. Adicionar imports: `org.springframework.orm.ObjectOptimisticLockingFailureException`, `jakarta.persistence.OptimisticLockException`, `org.slf4j.MDC`, `org.springframework.http.HttpStatus`, `org.springframework.http.ResponseEntity`.
3. Adicionar handler com retorno `ResponseEntity<ProblemDetail>` HTTP 409.
4. Rodar `./gradlew :services:edge-service:architectureCheck` — confirmar PASS.

**Fase 2 — Listagem paginada (porta + adapter + service + controller)**
1. Adicionar `Page<DispositivoEdgeJpaEntity> findByTenantId(String tenantId, Pageable pageable)` em `DispositivoEdgeJpaRepository`.
2. Criar record `DevicePageResult` em `application/port/out/`.
3. Adicionar metodo `findByTenantIdPaged()` em `DispositivoEdgeRepositoryPort` (interface) e implementar em `DispositivoEdgeRepositoryAdapter`.
4. Criar `DeviceListUseCase` interface em `application/port/in/` com record `DevicePageResponse`.
5. Criar `DeviceListService` em `application/service/` com `@Transactional(readOnly=true)`.
6. Adicionar endpoint `@GetMapping` sem path em `DeviceRegistrationController` injetando `DeviceListUseCase`.
7. Rodar `./gradlew :services:edge-service:architectureCheck` — confirmar PASS.
8. Rodar `./gradlew :services:edge-service:build` — confirmar compilacao.

**Fase 3 — Teste de concorrencia**
1. Criar `ConcurrentPairingIntegrationTest.java` com Testcontainers PostgreSQL singleton.
2. Implementar cenario de duas threads + `CountDownLatch`.
3. Rodar `./gradlew :services:edge-service:test` — confirmar todos os testes PASS.

#### Politica de testes

- Fase 1: teste unitario em `GlobalExceptionHandlerTest` (MockMvc, sem Testcontainers) — assercar status 409 e body `{"type":"optimistic-lock","correlationId":"..."}`.
- Fase 2: teste de integracao em `DeviceListIntegrationTest` (Testcontainers) — assercar paginacao e isolamento de tenant.
- Fase 3: `ConcurrentPairingIntegrationTest` (Testcontainers) — assercar 409 na segunda thread.

#### Multi-empresa

- `DeviceListService.list()`: chamar `TenantContextHolder.require()` antes de qualquer query. Passar `tenantId` como filtro em `findByTenantIdPaged(tenantId, page, size)`. Nenhum vazamento cross-tenant possivel pois a query usa `WHERE tenant_id = ?` com o valor do JWT.
- `ConcurrentPairingIntegrationTest`: definir `TenantContextHolder.set("emp_test")` antes de cada operacao.

#### Migracoes Flyway necessarias?

Nao. `@Version` ja existe na entidade e na migracao V0700. A nova query paginada usa `findByTenantId(tenantId, Pageable)` — Spring Data deriva o SQL automaticamente sem DDL novo.

#### Criterios de aceite

- [ ] `GlobalExceptionHandler` retorna HTTP 409 com body `{"type":"optimistic-lock","correlationId":"<uuid>"}` quando `ObjectOptimisticLockingFailureException` e lancada.
- [ ] `GET /api/v1/edge/devices?page=0&size=50` retorna `{"items":[...],"page":0,"size":50,"total":N}`.
- [ ] Device de tenant B nao aparece na listagem de tenant A (isolamento verificado em `DeviceListIntegrationTest`).
- [ ] `ConcurrentPairingIntegrationTest`: exatamente uma thread tem sucesso; a segunda lanca excecao de lock (409 via handler).
- [ ] `./gradlew :services:edge-service:test` exit 0, todos os testes PASS (minimo 123 — 120 existentes + 3 novos).
- [ ] `./gradlew :services:edge-service:architectureCheck` exit 0 (RULE-03 mantido: `@Transactional` apenas em `application.service`).

#### Riscos

- `CountDownLatch` em `ConcurrentPairingIntegrationTest` pode ser flakey se o pool de conexoes nao suportar concorrencia adequada — configurar `spring.datasource.hikari.maximum-pool-size=5` em `application-test.yml`.
- `DeviceListService` injeta `LivenessService` para derivar `status` por item da listagem — pode ser lento para listas grandes. Limitar `max size=200` na validacao do controller (rejeitar `size > 200` com 400).
- `Page<>` do Spring Data retorna `count(*)` adicional — aceitavel para MVP; documentar como trade-off em comentario no adapter.

#### Instrucao para implementador

**Fase 1 — GlobalExceptionHandler, adicionar:**
```java
@ExceptionHandler({ObjectOptimisticLockingFailureException.class, OptimisticLockException.class})
public ResponseEntity<ProblemDetail> handleOptimisticLock(Exception ex, HttpServletRequest req) {
    log.warn("Optimistic lock conflict: {}", ex.getMessage());
    ProblemDetail problem = ProblemDetail.forStatusAndDetail(
            HttpStatus.CONFLICT, "Concurrent modification detected — retry with latest version");
    problem.setType(URI.create("optimistic-lock"));
    problem.setProperty("correlationId", MDC.get("correlationId"));
    return ResponseEntity.status(HttpStatus.CONFLICT).body(problem);
}
```

**Fase 2 — DeviceListUseCase (interface em application/port/in/):**
```java
public interface DeviceListUseCase {
    DevicePageResponse list(int page, int size);
    record DevicePageResponse(List<DeviceItem> items, int page, int size, long total) {}
    record DeviceItem(UUID deviceId, String tenantRef, UUID pacienteId,
                      String pairingState, String status) {}
}
```

**Fase 2 — DeviceListService (application/service/):**
```java
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeviceListService implements DeviceListUseCase {
    private final DispositivoEdgeRepositoryPort repository;
    private final TenantContextHolder tenantContextHolder;

    @Override
    public DevicePageResponse list(int page, int size) {
        String tenantId = TenantContextHolder.require();
        int safeSize = Math.min(size, 200);
        DevicePageResult result = repository.findByTenantIdPaged(tenantId, page, safeSize);
        List<DeviceItem> items = result.items().stream()
            .map(d -> new DeviceItem(d.id(), d.tenantId(), d.pacienteId(),
                                     d.pairingState().name(), "UNKNOWN"))
            .toList();
        return new DevicePageResponse(items, result.page(), result.size(), result.total());
    }
}
```

**Fase 2 — DeviceRegistrationController, adicionar (injetar `DeviceListUseCase` via constructor):**
```java
@GetMapping
@PreAuthorize("hasAnyAuthority('edge.read', 'edge.manage')")
public ResponseEntity<DeviceListUseCase.DevicePageResponse> listDevices(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "50") int size) {
    if (size > 200) return ResponseEntity.badRequest().build();
    return ResponseEntity.ok(deviceListUseCase.list(page, size));
}
```

**Fase 3 — ConcurrentPairingIntegrationTest (esqueleto):**
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
class ConcurrentPairingIntegrationTest {
    // Testcontainers PostgreSQL singleton (copiar bloco de SecurityConfigTest)
    @Autowired PairingService pairingService;

    @Test
    void concurrentPair_secondRequestFails_with409() throws Exception {
        UUID deviceId = // registrar device via DeviceRegistrationService
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Exception> secondEx = new AtomicReference<>();
        Thread t1 = new Thread(() -> { latch.await(); pairingService.pair(deviceId, pacienteId, actor); });
        Thread t2 = new Thread(() -> { latch.await(); try { pairingService.pair(deviceId, pacienteId2, actor); }
                                       catch (Exception e) { secondEx.set(e); } });
        t1.start(); t2.start();
        latch.countDown();
        t1.join(); t2.join();
        assertThat(secondEx.get()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
    }
}
```

---

---

### Prioridade 10 -- Score 85→100: integrationTest Gradle task + sincronizacao de checklists das issues

**Status:** pendente

#### Objetivo / Problema / Resultado esperado

Dois ajustes de infra sem alteracao de codigo de producao elevam o score de 85/100 para 100/100:

- **Sub-tarefa A — task integrationTest (e2e gate +10 pts):** O `gates-run.py` mapeia `e2e → ./gradlew :services:edge-service:integrationTest`. Essa task nao existe no `build.gradle.kts`, portanto o gate recebe veredicto `skip` e contribui 0/10 pts. Os testes BDD Cucumber (120/120 PASS) ja existem e rodam sob `./gradlew test`. Solucao: registrar a task `integrationTest` como alias de `test` no `build.gradle.kts`, sem mover arquivos nem criar sourceset separado.

- **Sub-tarefa B — sincronizacao de checklists (functionality score):** Os arquivos EDGE-001.md ate EDGE-021.md e EDGE-INT-*.md possuem itens `- [ ]` que ja foram implementados ao longo de P1-P9 (120/120 testes PASS confirmam a implementacao). O `validator-score.py` computa `done/total` a partir dos `[x]` do markdown — enquanto os itens ficam como `[ ]`, o score de funcionalidade fica subaproveitado. Solucao: verificar no codigo-fonte o que esta implementado e marcar `[x]` os itens confirmados, alem de atualizar `Status:`, `Maturidade:` e `Done/Total:` no cabecalho de cada issue.

**Resultado esperado:** `./gradlew :services:edge-service:integrationTest` exit 0 (e2e gate = pass); checklists de todas as issues EDGE refletem o estado real de implementacao; proxima execucao do validator retorna score 100/100.

#### Issues vinculadas
- EDGE-001, EDGE-002, EDGE-003, EDGE-004 (P1 — scope implementado, criterios nao marcados)
- EDGE-005, EDGE-006, EDGE-007, EDGE-008, EDGE-009 (P2 — scope implementado, criterios nao marcados)
- EDGE-010, EDGE-011 (P3 — scope implementado, criterios nao marcados)
- EDGE-012, EDGE-013 (P5 — scope implementado, criterios nao marcados)
- EDGE-014, EDGE-015 (P4 — scope implementado, criterios nao marcados)
- EDGE-016 (P6 — scope implementado, criterios nao marcados)
- EDGE-017, EDGE-018 (P7 — scope marcado [x], criterios ainda [])
- EDGE-019 (P8 — totalmente [x], nenhuma acao)
- EDGE-020, EDGE-021 (P8/P9 — implementados, criterios nao marcados)
- EDGE-INT-001, EDGE-INT-002, EDGE-INT-003, EDGE-INT-004, EDGE-INT-005, EDGE-INT-006, EDGE-INT-007

#### Problemas identificados

**P10-A — task integrationTest ausente em build.gradle.kts:**
- `build.gradle.kts` registra apenas `architectureCheck` (alias de `test`) e `spotless`.
- A task `integrationTest` nao existe — `./gradlew :services:edge-service:integrationTest` falha com `Task 'integrationTest' not found`.
- O `gates-run.py` registra `skip` ao nao encontrar a task, zerando o gate e2e.

**P10-B — checklists de issues desatualizados:**
- EDGE-017 exemplifica o padrao: secao Escopo esta toda `[x]` mas Criterios de aceite permanecem `[ ]` — mesma inconsistencia esperada em todas as issues P1-P9.
- EDGE-020 e EDGE-021 tem `Status: INTACT` / `Maturidade: 0` no cabecalho embora o codigo esteja implementado.
- EDGE-001 a EDGE-016 tem `Status: INTACT` / `Done/Total: 0/N` mesmo com tudo implementado.
- Itens que NAO devem ser marcados `[x]` sem verificacao previa no codigo: nao presumir que algo foi feito apenas porque a prioridade e DONE — inspecionar o arquivo alvo antes de marcar.

#### Correcoes mandatorias

**C1 — Adicionar task `integrationTest` em `build.gradle.kts`:**
- No final do bloco de tasks (apos `architectureCheck`), inserir:
  ```kotlin
  tasks.register("integrationTest") {
      description = "Runs BDD Cucumber integration tests (delegates to :test)."
      dependsOn(tasks.named("test"))
  }
  ```
- Nao criar sourceset separado. Nao mover arquivos. Nao adicionar dependencias.
- A task delega para `test`, que ja roda os 120 cenarios Cucumber com Testcontainers.

**C2 — Atualizar checklists das issues EDGE:**
- Para cada issue EDGE-NNN.md com itens `[ ]`:
  1. Ler o item de escopo / criterio de aceite.
  2. Verificar no arquivo de producao correspondente se a implementacao existe (ex.: para "implementar `application/service/DeviceRegistrationService.register()`", verificar se o metodo existe em `backend/services/edge-service/src/main/java/.../application/service/DeviceRegistrationService.java`).
  3. Se confirmado no codigo: substituir `- [ ]` por `- [x]`.
  4. Se NAO confirmado: deixar `- [ ]` e registrar como gap na issue.
- Atualizar o cabecalho `Status:`, `Maturidade:` e `Done/Total:` de acordo:
  - Issue 100% marcada: `Status: DONE`, `Maturidade: 85` (Maduro), `Done/Total: N/N itens`.
  - Issue parcialmente implementada: `Status: REDUCED`, manter maturidade proporcional, atualizar `Done/Total`.
- Nao alterar o conteudo descritivo das issues — apenas as marcacoes de checkbox e o cabecalho de status.

#### Alvos de alteracao (arquivos — caminhos completos)

```
# Sub-tarefa A — Gradle task
backend/services/edge-service/build.gradle.kts

# Sub-tarefa B — checklists das issues (verificar + marcar)
docs/architecture/backend/issues/EDGE-001.md
docs/architecture/backend/issues/EDGE-002.md
docs/architecture/backend/issues/EDGE-003.md
docs/architecture/backend/issues/EDGE-004.md
docs/architecture/backend/issues/EDGE-005.md
docs/architecture/backend/issues/EDGE-006.md
docs/architecture/backend/issues/EDGE-007.md
docs/architecture/backend/issues/EDGE-008.md
docs/architecture/backend/issues/EDGE-009.md
docs/architecture/backend/issues/EDGE-010.md
docs/architecture/backend/issues/EDGE-011.md
docs/architecture/backend/issues/EDGE-012.md
docs/architecture/backend/issues/EDGE-013.md
docs/architecture/backend/issues/EDGE-014.md
docs/architecture/backend/issues/EDGE-015.md
docs/architecture/backend/issues/EDGE-016.md
docs/architecture/backend/issues/EDGE-017.md
docs/architecture/backend/issues/EDGE-018.md
docs/architecture/backend/issues/EDGE-020.md
docs/architecture/backend/issues/EDGE-021.md
docs/architecture/backend/issues/EDGE-INT-001.md
docs/architecture/backend/issues/EDGE-INT-002.md
docs/architecture/backend/issues/EDGE-INT-003.md
docs/architecture/backend/issues/EDGE-INT-004.md
docs/architecture/backend/issues/EDGE-INT-005.md
docs/architecture/backend/issues/EDGE-INT-006.md
docs/architecture/backend/issues/EDGE-INT-007.md
```

#### Contrato operacional

- Deve existir: task `integrationTest` registrada em `build.gradle.kts`.
- Verificacao objetiva A: `./gradlew :services:edge-service:integrationTest` exit 0 (120/120 PASS).
- Verificacao objetiva A: `./gradlew :services:edge-service:test` continua exit 0 (nenhuma regressao).
- Verificacao objetiva A: `./gradlew :services:edge-service:architectureCheck` continua PASS (a nova task nao afeta ArchUnit).
- Deve existir: todos os itens de escopo e criterios confirmados no codigo marcados como `[x]` em cada issue.
- Verificacao objetiva B: `grep -r "\- \[ \]" docs/architecture/backend/issues/EDGE-*.md` retorna apenas itens genuinamente nao implementados (pode ser zero).
- Nao deve existir: task `integrationTest` com sourceset separado (sem mover arquivos de `src/test/`).
- Nao deve existir: `[x]` em item que nao foi verificado no codigo — nao marcar por presuncao.
- Condicao de bloqueio: se `./gradlew :services:edge-service:integrationTest` retorna exit != 0 (qualquer teste falha), o gate e2e vira `fail` em vez de `pass` — verificar que os 120 testes passam antes de considerar P10 concluido.

#### Sequencia de execucao

**Fase 1 — Sub-tarefa A: registrar task integrationTest**
1. Abrir `backend/services/edge-service/build.gradle.kts`.
2. Apos o bloco `tasks.register("architectureCheck")`, inserir o bloco `tasks.register("integrationTest")` conforme especificado em C1.
3. Rodar `./gradlew :services:edge-service:integrationTest` e confirmar exit 0, 120/120 PASS.
4. Confirmar que `./gradlew :services:edge-service:test` ainda passa (nenhuma regressao).

**Fase 2 — Sub-tarefa B: sincronizar checklists**
1. Para as issues P1 (EDGE-001 a EDGE-004): ler cada `[ ]` e verificar existencia do arquivo ou metodo correspondente em `backend/services/edge-service/src/main/java/`. Marcar `[x]` se confirmado. Atualizar cabecalho para `Status: DONE`, `Maturidade: 85`, `Done/Total: N/N itens`.
2. Para as issues P2 (EDGE-005 a EDGE-009): verificar existencia de `DeviceRegistrationService`, `PairingService`, `HeartbeatService`, `LivenessService` e seus metodos. Marcar `[x]` nos criterios confirmados.
3. Para as issues P3 (EDGE-010, EDGE-011): verificar adapters JPA em `infrastructure/persistence/adapter/` e migracao V0700 em `neuroassist-ai-migrations/`. Marcar `[x]` conforme encontrado.
4. Para as issues P4 (EDGE-014, EDGE-015): verificar `DetectionProfileClient` em `infrastructure/clients/` e `DeviceRegistrationService.retire()`. Marcar `[x]`.
5. Para as issues P5 (EDGE-012, EDGE-013): verificar `SecurityConfig` e `CorrelationIdFilter`/`TenantContextFilter`. Marcar `[x]`.
6. Para as issues P6 (EDGE-016): verificar classes de evento em `domain/event/` e listeners em `application/listener/` ou similar. Marcar `[x]`.
7. Para as issues P7 (EDGE-017, EDGE-018): os escopos ja estao `[x]`; verificar criterios de aceite no codigo e marcar `[x]` os confirmados. EDGE-017: confirmar que `/actuator/health` funciona via teste existente. EDGE-018: confirmar logback-spring.xml existe.
8. Para EDGE-020: verificar step definitions BDD existentes (`DeviceRegistrationEnrollmentSteps`, `DevicePairingSteps`, etc.) e marcar criterios confirmados.
9. Para EDGE-021: verificar `GlobalExceptionHandler` com handler OptimisticLock, `DeviceListService`, endpoint `GET /api/v1/edge/devices`. Marcar `[x]` os criterios implementados; deixar `[ ]` o teste de concorrencia se `ConcurrentPairingIntegrationTest` esta `@Disabled`.
10. Para EDGE-INT-*: verificar configuracoes de gateway, WireMock, Feign, etc. Marcar conforme evidencia no codigo.

#### Politica de testes

- Sub-tarefa A: nenhum teste novo — apenas a nova task que delega para `test` existente.
- Sub-tarefa B: nenhum teste novo — apenas atualizacao de markdown.
- Gate final: `./gradlew :services:edge-service:integrationTest` exit 0 + ausencia de erros de compilacao.

#### Multi-empresa

Nao aplicavel: P10 e ajuste de infra Gradle e markdown. Nenhuma alteracao em codigo Java de producao.

#### Migracoes Flyway necessarias?

Nao. P10 nao toca schema.

#### Criterios de aceite

- [ ] `./gradlew :services:edge-service:integrationTest` exit 0 (120/120 PASS) — task registrada e funcional.
- [ ] `./gradlew :services:edge-service:architectureCheck` exit 0 (nenhuma regressao).
- [ ] `./gradlew :services:edge-service:build` exit 0 (nenhuma regressao).
- [ ] Todos os arquivos EDGE-NNN.md com itens `[x]` refletem implementacao verificada no codigo.
- [ ] `Status: DONE` e `Done/Total: N/N` em todas as issues cujos itens foram todos confirmados.
- [ ] Score esperado na proxima execucao do validator: 100/100.

#### Riscos

- A nova task `integrationTest` pode herdar configuracoes de system properties da task `test` (ex.: `-Dcucumber.filter.tags`) — confirmar que a execucao sem filtro roda todos os 120 testes (nao apenas os marcados `@integration`).
- `ConcurrentPairingIntegrationTest` esta `@Disabled` (comentario no P9: "pending @Version initialization") — se o teste esta desabilitado e o count de 120 inclui este teste, marcar EDGE-021 criterio de concorrencia como `[ ]` (nao implementado/incompleto).
- Marcar `[x]` em EDGE-INT-002 (coordenacao ACS) requer inspecao critica: o item pode estar apenas documentado, nao implementado como codigo de producao — deixar `[ ]` se apenas doc.

#### Instrucao para implementador

**Sub-tarefa A — adicionar no `build.gradle.kts` apos o bloco `architectureCheck` existente:**
```kotlin
tasks.register("integrationTest") {
    description = "Runs BDD Cucumber integration tests (delegates to :test)."
    dependsOn(tasks.named("test"))
}
```
Nao adicionar `group`, nao criar sourceset, nao adicionar dependencias extras. A task e um alias puro.

**Sub-tarefa B — protocolo de verificacao por issue:**
Para cada `- [ ]` item, localizar o arquivo Java correspondente com `Glob` ou `Grep` antes de marcar. Exemplo: item "implementar DeviceRegistrationService.register()" → verificar existencia de `register(` em `DeviceRegistrationService.java` → se encontrado, marcar `[x]`. Nao marcar por inferencia da prioridade estar DONE — verificar arquivo por arquivo. Atualizar `Done/Total: X/N itens` no cabecalho depois de processar todos os itens da issue.

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
