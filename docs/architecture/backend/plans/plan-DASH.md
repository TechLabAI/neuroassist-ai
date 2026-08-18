# Plano -- DASH (dash-service)

> **NeuroAssist AI** — Backend-for-Frontend do painel do cuidador.
> Porta `8090` | Java 21 / Spring Boot 3.5.x | Zero tabelas MVP (ADR-004) | SSE + write-through

---

## Status das prioridades

- P1 -- pendente
- P2 -- pendente
- P3 -- pendente
- P4 -- pendente
- P5 -- pendente
- P6 -- pendente
- P7 -- pendente
- P8 -- pendente

---

## 0. Leitura executiva

### Papel do servico

`dash-service` e o Backend-for-Frontend do painel do cuidador. Ele e o unico ponto de acesso
do Angular panel aos dados e acoes: compo as informacoes de 5 servicos donos (ACS, PAC, EVNT,
HIST, EDGE), entrega notificacoes em tempo real via SSE (`/panel/stream`), e faz proxy de
escrita das acoes do cuidador (calibracao → PAC, confirmacao de evento → EVNT, ack de notificacao → EVNT).

### MVP scope

- **Zero tabelas DASH** (ADR-004): nenhum store duravel no MVP. `PanelSession` e o unico estado
  em memoria (SSE session registry, efemero).
- **Sem broker** (`stack.messaging.enabled = false`, ADR-005): EVNT notifica o DASH via REST
  `POST /internal/notifications`; o DASH notifica o browser via SSE.
- **Write-through apenas**: calibracao (PAC) e confirmacao/ack (EVNT). DASH nao armazena resultado.
- **Porta 8090**, registrado no Eureka (REG), roteado pelo GWY para o browser.
- **Superficies**: `/panel/**` (JWT do cuidador) e `/internal/**` (authority `SVC_EVNT`; nao exposta via GWY ao browser).

### Diagnostico inicial (gaps mapeados)

- Nenhum codigo gerado ate agora (servico greenfield). Nao ha drift.
- Sem Flyway, sem datasource: geradores e validators devem pular qualquer step de migracao V1.
- Idempotencia por `dispatchId` e critica: implementada em memoria (Set na PanelSession); o EVNT
  e responsavel pelo retry caso `no_open_session`.
- Degradacao assimetrica: EDGE e EVNT-read sao soft-degrade; ACS, PAC e HIST sao hard-503.
  O circuit breaker deve refletir isso.

---

## 1. Estrategia geral

### Objetivo macro

Implementar o BFF do painel do cuidador com SSE funcional, composicao cross-module, write-throughs
e seguranca em duas superficies — sem nenhuma persistencia de dominio.

### Ordem das prioridades

| P | Titulo | Motivo |
|---|---|---|
| P1 | Scaffold + ports + value objects | Base hexagonal sem DB; bloqueia tudo |
| P2 | SSE session + dispatch sink | Golden path: painel conecta + recebe notificacao |
| P3 | Composicao de leitura | Telas home, overview, historico, device |
| P4 | Write-throughs (calibracao, confirmacao, ack) | Acoes do cuidador → proxies para donos |
| P5 | Adapters (5 upstreams) | HTTP real atras dos ports; WireMock tests |
| P6 | Seguranca + degradacao matrix | OAuth2 + circuit breakers por upstream |
| P7 | Observabilidade | MDC, Actuator, metrica SSE sessions |
| P8 | BDD end-to-end | Suite Cucumber (3 features) |

---

## 2. Plano por prioridade

### Prioridade 1 -- Scaffold + Ports + Value Objects

**Status:** pendente

#### Objetivo / Resultado esperado
Projeto Spring Boot `dash-service` criado no Gradle multi-module, sem JPA/Flyway, com
estrutura hexagonal e todos os ports e value objects definidos. `./gradlew :dash-service:build`
passa sem erros.

#### Issues vinculadas
- DASH-001 (Gradle subproject, Spring Boot, no-DB)
- DASH-002 (hexagonal outbound ports)
- DASH-003 (transient value objects)

#### Specs cobertas
- SPEC-01, SPEC-04, SPEC-10

#### Sequencia de execucao
- Fase 1: DASH-001 (Gradle + Spring Boot base)
- Fase 2: DASH-002 (6 interfaces de port em `domain/port`)
- Fase 3: DASH-003 (5 value objects em `domain/model`)

#### Criterios de aceite
- `./gradlew :dash-service:build` verde
- `/actuator/health` responde `200 UP` sem datasource
- 6 ports e 5 value objects presentes sem anotacoes JPA

#### Riscos
- JPA autoimportado via dependencia transitiva → verificar classpath explicitamente

#### Instrucao para implementador
Criar o modulo `:dash-service` em `backend/settings.gradle.kts`. Build sem JPA, sem datasource,
sem Flyway. Verificar que `./gradlew :dash-service:dependencies | grep jpa` nao retorna nada.

---

### Prioridade 2 -- SSE Session + Dispatch Sink

**Status:** pendente

#### Objetivo / Resultado esperado
Canal SSE funcional (`/panel/stream`) com registro de sessao em memoria e heartbeat. EVNT
consegue entregar notificacoes via `POST /internal/notifications`; painel recebe em < 1s.

#### Issues vinculadas
- DASH-004 (PanelSessionRegistry com eviction)
- DASH-005 (PanelStreamController)
- DASH-006 (InternalNotificationController + idempotencia dispatchId)
- INT-DASH-001 (contrato dispatch sink + auth SVC_EVNT)
- INT-DASH-002 (callbacks delivered/acknowledged)

#### Specs cobertas
- SPEC-01, SPEC-03, SPEC-04, SPEC-05

#### Sequencia de execucao
- Fase 1: DASH-004 (PanelSessionRegistry)
- Fase 2: DASH-005 (PanelStreamController) — depende de DASH-004 + DASH-021 (auth em P6 → testar com MockMvc desabilitando security em teste de slice)
- Fase 3: DASH-006 (dispatch sink) — depende de DASH-004
- Fase 4: INT-DASH-001 + INT-DASH-002 (testes de contrato WireMock)

#### Criterios de aceite
- SSE stream mantido aberto com heartbeat a cada `HEARTBEAT_SECONDS`
- Dispatch com sessao ativa → push SSE + `delivered` callback
- Dispatch sem sessao → `202 no_open_session`
- Redelivery idempotente

#### Riscos
- Concorrencia em `PanelSessionRegistry`: usar `ConcurrentHashMap` e `synchronized` em eviction
- SSE timeout antes do heartbeat: configurar `SseEmitter(timeout=0)` e delegar eviction ao scheduler

#### Instrucao para implementador
Implementar na ordem: registry → stream controller → dispatch sink. Executar testes de slice
`@WebMvcTest` para stream e `@SpringBootTest` para dispatch. Auth pode ser mockada em slice tests.

---

### Prioridade 3 -- Composicao de Leitura

**Status:** pendente

#### Objetivo / Resultado esperado
Quatro endpoints de leitura compostos funcionais: `GET /panel/summary` (home dashboard),
`GET /panel/patients/{id}/overview` (per-patient), `GET /panel/patients/{id}/history` (HIST),
`GET /panel/patients/{id}/device` (EDGE). Degradacao soft/hard implementada.

#### Issues vinculadas
- DASH-007 (summary: EVNT + EDGE + ACS)
- DASH-008 (overview: PAC + EDGE)
- DASH-009 (history: HIST)
- DASH-010 (device: EDGE liveness + ETag + staleness)
- DASH-011 (on-connect catch-up replay)
- DASH-012 (long-poll fallback)
- INT-DASH-004 (HIST: pagination forwarding, size cap)
- INT-DASH-005 (EDGE: ETag/304 + staleness-to-unknown)

#### Specs cobertas
- SPEC-01, SPEC-02, SPEC-03, SPEC-07

#### Sequencia de execucao
- Fase 1: DASH-007 + DASH-008 (composicao summary + overview, mocks dos ports)
- Fase 2: DASH-009 + DASH-010 (history + device)
- Fase 3: DASH-011 + DASH-012 (catch-up + long-poll)
- Fase 4: INT-DASH-004 + INT-DASH-005 (testes de contrato WireMock)

#### Criterios de aceite
- `GET /panel/summary` retorna `SummaryView` com `degradedSections` correto
- EDGE/EVNT soft-degrade: 200 com secao omitida + flag
- ACS/PAC/HIST hard: 503
- `size` capado a 100 antes de chamar HIST
- ETag/304 honrado no poll EDGE

#### Riscos
- Composicao paralela (summary chama EVNT + EDGE por paciente): usar `CompletableFuture` com timeout `UPSTREAM_TIMEOUT_MS`
- EDGE staleness race condition: timestamp de `lastSuccessAt` deve ser monotono

#### Instrucao para implementador
Implementar ports mockados com `@MockBean` em `@WebMvcTest`. Adapters reais vem em P5.
Priorizar DASH-007 (tela principal) e DASH-011 (catch-up e dependencia do SSE de P2).

---

### Prioridade 4 -- Write-throughs

**Status:** pendente

#### Objetivo / Resultado esperado
Tres acoes do cuidador funcionais via proxy: calibracao → PAC, confirmacao de evento → EVNT,
ack de notificacao → EVNT. Erros dos donos sao repassados fielmente (relay).

#### Issues vinculadas
- DASH-013 (calibracao write-through PAC)
- DASH-014 (confirmacao EVNT)
- DASH-015 (ack EVNT)
- INT-DASH-003 (PAC: ProblemDetail relay)

#### Specs cobertas
- SPEC-01, SPEC-02, SPEC-05

#### Sequencia de execucao
- DASH-013 → DASH-014 → DASH-015 (independentes entre si, ordenados por criticidade)
- INT-DASH-003 (WireMock PAC aceita/rejeita)

#### Criterios de aceite
- Calibracao: PAC aceita → 200 echo; PAC 422 → relay 422 sem modificacao
- Confirmacao: EVNT aceita → 200 echo; `feedback` invalido → 422 antes de chamar EVNT
- Ack: idempotente; EVNT down → 503 (nao silenciar falha)

#### Riscos
- `feedback` enum: validar `@Pattern` ou `@JsonCreator` antes de chamar EVNT para evitar 500 por enum desconhecido

#### Instrucao para implementador
Nenhum dado e salvo: verificar que nem cache nem campo mapeado aponta para write apos o proxy.
Em todos os write-throughs: sem retry automatico (owner service gerencia idempotencia — ADR-006).

---

### Prioridade 5 -- Adapters (5 upstreams)

**Status:** pendente

#### Objetivo / Resultado esperado
Seis adapters Feign implementados (ACS, PAC, 2x EVNT, HIST, EDGE), com WireMock tests por
adapter. Cada adapter instancia a dependencia HTTP atras de seu port correspondente.

#### Issues vinculadas
- DASH-016 (AcsRestAdapter: JWK cache + scope cache)
- DASH-017 (PacRestAdapter)
- DASH-018 (EvntEventRestAdapter + EvntDispatchCallbackAdapter)
- DASH-019 (HistRestAdapter)
- DASH-020 (EdgeLivenessRestAdapter: ETag + staleness)
- INT-DASH-006 (ACS: cache TTL + JWK + hard-503)

#### Specs cobertas
- SPEC-06, SPEC-10

#### Sequencia de execucao
- Fase 1: DASH-016 (ACS — mais critico: bloqueia scope resolution)
- Fase 2: DASH-017 + DASH-018 (PAC + EVNT — write-throughs de P4)
- Fase 3: DASH-019 + DASH-020 (HIST + EDGE — leituras de P3)
- Fase 4: INT-DASH-006

#### Criterios de aceite
- Cada adapter tem WireMock tests: ok, upstream-down, error-relay
- Scope cache: 1 request ACS por TTL (medido via WireMock verify)
- EdgeLivenessRestAdapter: ETag/304 honorado, staleness correto

#### Riscos
- Escopo de Caffeine cache vs `ConcurrentHashMap` com TTL manual: preferir Caffeine (java.util nao tem TTL nativo)
- Feign com suporte a `304`: Feign padrao trata 304 como erro; usar `RestTemplate` com `exchange()` para EDGE se necessario

#### Instrucao para implementador
Implementar adapters bottom-up (ACS primeiro pois e pre-requisito de scope em P3). Cada
adapter tem `@Component` e `implements <Port>`. Nenhum negocio nos adapters — so mapeamento
HTTP <-> tipos de dominio.

---

### Prioridade 6 -- Seguranca + Degradacao Matrix

**Status:** pendente

#### Objetivo / Resultado esperado
`SecurityConfig` configurado com duas superficies de auth (JWT panel + SVC_EVNT). Circuit
breakers por upstream com politica soft/hard. GWY roteando `/panel/**` publico e bloqueando
`/internal/**`.

#### Issues vinculadas
- DASH-021 (OAuth2 resource server + SecurityConfig)
- DASH-022 (degradacao matrix: circuit breakers)
- INT-DASH-007 (GWY routing)

#### Specs cobertas
- SPEC-06, SPEC-02, SPEC-10

#### Sequencia de execucao
- DASH-021 (SecurityConfig — desbloqueia testes de integracao reais)
- DASH-022 (Resilience4j circuit breakers)
- INT-DASH-007 (GWY config — pode ser em paralelo)

#### Criterios de aceite
- `/panel/**` sem JWT → 401; `/internal/**` sem SVC_EVNT → 401
- `tenant` claim ausente → 401
- EDGE/EVNT-read down → soft-degrade (200 com degradedSections)
- ACS/PAC/HIST down → hard-503

#### Riscos
- Ordem dos `requestMatchers` em SecurityConfig: `/internal/**` deve ser configurado antes de `/panel/**` para evitar fallthrough
- Circuit breaker timeout vs upstream timeout: configurar Resilience4j `timeLimiter` = `UPSTREAM_TIMEOUT_MS`

#### Instrucao para implementador
Configurar `SecurityFilterChain` com dois filtros de autenticacao distintos (um para cada
superfice). Resilience4j configurado via `application.yml` por upstream ID.

---

### Prioridade 7 -- Observabilidade

**Status:** pendente

#### Objetivo / Resultado esperado
`X-Correlation-Id` propagado em toda request (MDC + outbound Feign headers). Logging JSON
em prod. Gauge `dashboard.sse.sessions.active`. Actuator health endpoint disponivel.

#### Issues vinculadas
- DASH-023 (Actuator + MDC + gauge)

#### Specs cobertas
- SPEC-07

#### Sequencia de execucao
- DASH-023 (unica issue desta prioridade)

#### Criterios de aceite
- Toda request rastreavel por correlationId no log
- `correlationId` presente em todos os ProblemDetail
- `/actuator/health` sem auth

#### Riscos
- Feign `RequestInterceptor` pode perder o MDC em threads async (SSE + CompletableFuture): propagar manualmente

#### Instrucao para implementador
Implementar `CorrelationIdFilter` como `@Bean OncePerRequestFilter`. Para threads async:
capturar MDC no request thread e propagar via `ContextSnapshot` (Micrometer Observation) ou
`MDC.getCopyOfContextMap()` antes de submeter o `CompletableFuture`.

---

### Prioridade 8 -- BDD End-to-End

**Status:** pendente

#### Objetivo / Resultado esperado
Suite Cucumber (3 features) passando com WireMock para todos os upstreams.
Sem banco real, sem broker real, sem Eureka real.

#### Issues vinculadas
- DASH-024 (Cucumber: home-status-calm, notification-delivery, calibration-writethrough)

#### Specs cobertas
- SPEC-08

#### Sequencia de execucao
- DASH-024 (apos P1..P7)

#### Criterios de aceite
- `./gradlew :dash-service:test` com tag `@cucumber` executa 3 features sem falhas
- WireMock verifica callbacks delivered + acknowledged
- Cenario de rejeicao PAC: relay 422 fiel

#### Riscos
- SSE em `@SpringBootTest`: usar `TestRestTemplate` com streaming ou mock do `PanelSessionRegistry` para simular push

#### Instrucao para implementador
Usar `@SpringBootTest(webEnvironment = RANDOM_PORT)` com WireMock como `@ClassRule`. Cada
feature e um cenario de aceitacao independente; nao compartilhar estado entre features.

---

## 3. Dependencias externas

| Servico | Tipo | Issues impactadas |
|---|---|---|
| ACS (`acs-service`, :8088) | Hard (scope + JWK) | DASH-016, DASH-021, INT-DASH-006 |
| PAC (`pac-service`, :8082) | Hard (overview + calibracao) | DASH-008, DASH-013, DASH-017, INT-DASH-003 |
| EVNT (`evnt-service`, :8085) | Hard (callbacks) + Soft (read) | DASH-006, DASH-014, DASH-015, DASH-018, INT-DASH-001, INT-DASH-002 |
| HIST (`hist-service`, :8089) | Hard (historico) | DASH-009, DASH-019, INT-DASH-004 |
| EDGE (`edge-service`, :8087) | Soft (liveness) | DASH-010, DASH-020, INT-DASH-005 |
| GWY (:8080) | Infra (routing) | INT-DASH-007 |
| REG (:8761) | Infra (discovery) | DASH-001 |

---

## 4. Restricoes arquiteturais

- **ADR-004 (BFF zero-state):** DASH NAO persiste nenhum dado de dominio no MVP. Qualquer escrita local alem de `PanelSession` (in-memory) viola esta ADR.
- **ADR-005 (broker-deferred):** Sem consumers/producers de broker. EVNT→DASH e REST; browser→DASH e SSE.
- **ADR-006 (write-through):** Calibracao e PAC, confirmacao/ack e EVNT. DASH e puro proxy; nao armazena resultado.
- **ArchUnit RULE-01:** `api` depende de `application`; `application` depende de `domain`; `infrastructure` depende de `domain`. `application` e `domain` NAO dependem de `infrastructure`.
- **ArchUnit RULE-02:** `@Transactional` somente em `application.service` (N/A: sem JPA, mas regra deve ser verificada para evitar anotacoes acidentais).
- **ArchUnit RULE-03:** Nenhuma importacao HTTP (`org.springframework.web.*`, `feign.*`) em `domain` ou `application`.
- **LGPD:** DASH nao persiste dados biometricos ou clinicos. Sessoes SSE sao efemeras. `caregiver_ui_preference` e diferida e fora do MVP.
- **Sem Flyway:** `spring.flyway.enabled=false` obrigatorio. Sem scripts `V1__*.sql`.

---

## 5. Referencias

```
docs/architecture/backend/services/dash-service/
  ├── 01-architectural-vision.md
  ├── 02-domain-model.md
  ├── 03-c4-component.md
  ├── 04-api-design.md
  ├── 05-integration-design.md
  ├── 06-non-functional-requirements.md
  ├── adr/
  │   ├── ADR-004-bff-zero-state.md
  │   ├── ADR-005-notification-delivery-sse-broker-deferred.md
  │   └── ADR-006-write-through-only.md
  ├── contracts/
  │   ├── api-contract.md
  │   ├── data-model.md
  │   ├── event-contracts.md
  │   └── integration-contracts.md
  ├── spec/backend/
  │   ├── SPEC-01-api-surface.md
  │   ├── SPEC-02-panel-composition-bff.md
  │   ├── SPEC-03-notification-delivery-sse.md
  │   ├── SPEC-04-data-model-migrations.md
  │   ├── SPEC-05-idempotency-concurrency.md
  │   ├── SPEC-06-security.md
  │   ├── SPEC-07-observability-health.md
  │   ├── SPEC-08-bdd-test-scenarios.md
  │   ├── SPEC-09-messaging-architecture.md
  │   └── SPEC-10-integrations.md
  ├── events/event-catalog.md
  ├── generator-config.yml
  └── setup-local.md

docs/architecture/backend/issues/
  ├── DASH-001..DASH-024.md     (issues funcionais)
  ├── INT-DASH-001..INT-DASH-007.md  (issues de integracao)
  └── issues_map-DASH.md

docs/architecture/backend/plans/
  └── plan-DASH.md              (este arquivo)
```
