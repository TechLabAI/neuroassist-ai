# Issues Map — DASH (dash-service)

> BFF do painel do cuidador. Zero tabelas MVP (ADR-004). SSE + write-through.
> Total: 24 issues funcionais + 7 issues de integracao = **31 issues**
> Gerado em: 2026-08-17

---

## Por SPEC (fonte)

| SPEC | Issues |
|---|---|
| SPEC-01 (API surface) | DASH-001, DASH-005, DASH-006, DASH-007, DASH-008, DASH-009, DASH-010, DASH-011, DASH-012, DASH-013, DASH-014, DASH-015, DASH-021 |
| SPEC-02 (Composicao BFF) | DASH-003, DASH-007, DASH-008, DASH-009, DASH-010, DASH-013, DASH-014, DASH-022 |
| SPEC-03 (SSE delivery) | DASH-004, DASH-005, DASH-006, DASH-011, DASH-012, DASH-015 |
| SPEC-04 (Data model: zero tabelas) | DASH-001, DASH-003, DASH-004 |
| SPEC-05 (Idempotencia) | DASH-004, DASH-006, DASH-011, DASH-015, DASH-018 |
| SPEC-06 (Security) | DASH-016, DASH-021 |
| SPEC-07 (Observabilidade) | DASH-010, DASH-020, DASH-023 |
| SPEC-08 (BDD) | DASH-024 |
| SPEC-09 (Messaging: broker-deferred) | N/A (sem issues de broker no MVP) |
| SPEC-10 (Integracoes) | DASH-002, DASH-016, DASH-017, DASH-018, DASH-019, DASH-020, DASH-022 |

---

## Por prioridade

| P | Issues funcionais | Issues de integracao |
|---|---|---|
| P1 | DASH-001, DASH-002, DASH-003 | — |
| P2 | DASH-004, DASH-005, DASH-006 | INT-DASH-001, INT-DASH-002 |
| P3 | DASH-007, DASH-008, DASH-009, DASH-010, DASH-011, DASH-012 | INT-DASH-004, INT-DASH-005 |
| P4 | DASH-013, DASH-014, DASH-015 | INT-DASH-003 |
| P5 | DASH-016, DASH-017, DASH-018, DASH-019, DASH-020 | INT-DASH-006 |
| P6 | DASH-021, DASH-022 | INT-DASH-007 |
| P7 | DASH-023 | — |
| P8 | DASH-024 | — |

---

## Por camada hexagonal (layer)

| Layer | Issues |
|---|---|
| `api` | DASH-005 (PanelStreamController), DASH-006 (InternalNotificationController), DASH-007..015 (PanelController endpoints) |
| `application` | DASH-004 (PanelSessionRegistry), DASH-007..012 (PanelCompositionService), DASH-011 (PanelStreamService.replayCatchup), DASH-013..015 (write-through proxies) |
| `domain` | DASH-002 (ports), DASH-003 (value objects) |
| `infrastructure` | DASH-016 (AcsRestAdapter), DASH-017 (PacRestAdapter), DASH-018 (EvntRestAdapters), DASH-019 (HistRestAdapter), DASH-020 (EdgeLivenessRestAdapter) |
| `cross-cutting` | DASH-001 (Gradle+config), DASH-021 (OAuth2), DASH-022 (degradation matrix + circuit breakers), DASH-023 (Actuator+logging+MDC) |
| `bdd` | DASH-024 (Cucumber) |
| `integration` | INT-DASH-001..007 |

---

## Por modulo de integracao (upstream)

| Upstream | Issues de integracao |
|---|---|
| EVNT | INT-DASH-001 (dispatch sink), INT-DASH-002 (callbacks) |
| PAC | INT-DASH-003 (calibration write-through) |
| HIST | INT-DASH-004 (history query) |
| EDGE | INT-DASH-005 (liveness condicional) |
| ACS | INT-DASH-006 (scope cache + JWK) |
| GWY | INT-DASH-007 (routing /panel vs /internal) |

---

## Issues funcionais — lista completa

| # | Titulo | P | Size | Specs |
|---|---|---|---|---|
| DASH-001 | Create dash-service Gradle subproject (Spring Boot, no-DB, hexagonal skeleton) | P1 | M | SPEC-01, SPEC-04 |
| DASH-002 | Define hexagonal outbound ports for all upstream integrations | P1 | M | SPEC-10, SPEC-01 |
| DASH-003 | Define transient view-model and envelope value objects (no JPA entities) | P1 | S | SPEC-02, SPEC-04 |
| DASH-004 | Implement PanelSession in-memory registry with heartbeat eviction | P2 | M | SPEC-03, SPEC-04, SPEC-05 |
| DASH-005 | Implement PanelStreamController: GET /panel/stream SSE channel with heartbeat | P2 | M | SPEC-01, SPEC-03 |
| DASH-006 | Implement InternalNotificationController: POST /internal/notifications dispatch sink | P2 | M | SPEC-01, SPEC-03, SPEC-05 |
| DASH-007 | Implement GET /panel/summary: cross-module home view-model composition | P3 | M | SPEC-01, SPEC-02 |
| DASH-008 | Implement GET /panel/patients/{id}/overview: per-patient composition (PAC + EDGE) | P3 | M | SPEC-01, SPEC-02 |
| DASH-009 | Implement GET /panel/patients/{id}/history: paged read-through to HIST | P3 | S | SPEC-01, SPEC-02 |
| DASH-010 | Implement GET /panel/patients/{id}/device: EDGE liveness poll ETag/304 staleness | P3 | M | SPEC-01, SPEC-02, SPEC-07 |
| DASH-011 | Implement on-connect catch-up replay: unacknowledged + catchup-complete | P3 | S | SPEC-03, SPEC-05 |
| DASH-012 | Implement long-poll fallback: GET /panel/notifications/poll | P3 | S | SPEC-01, SPEC-03 |
| DASH-013 | Implement PUT /panel/.../calibration: write-through to PAC with error relay | P4 | S | SPEC-01, SPEC-02 |
| DASH-014 | Implement POST /panel/events/{id}/confirm: write-through to EVNT | P4 | S | SPEC-01, SPEC-02 |
| DASH-015 | Implement POST /panel/notifications/{dispatchId}/acknowledge: write-through EVNT | P4 | S | SPEC-01, SPEC-03, SPEC-05 |
| DASH-016 | Implement AcsRestAdapter (IdentityDirectoryPort): JWK-set + scope cache TTL=30s | P5 | M | SPEC-06, SPEC-10 |
| DASH-017 | Implement PacRestAdapter (CommandCatalogPort): catalog + calibration write-through | P5 | S | SPEC-10 |
| DASH-018 | Implement EvntEventRestAdapter + EvntDispatchCallbackAdapter | P5 | M | SPEC-10, SPEC-05 |
| DASH-019 | Implement HistRestAdapter (HistoryQueryPort) | P5 | S | SPEC-10 |
| DASH-020 | Implement EdgeLivenessRestAdapter (DeviceLivenessPort): ETag + staleness | P5 | M | SPEC-10, SPEC-07 |
| DASH-021 | Configure OAuth2 resource server: JWT panel + SVC_EVNT authority /internal/** | P6 | M | SPEC-06 |
| DASH-022 | Implement degradation matrix: circuit breakers soft/hard por upstream | P6 | M | SPEC-02, SPEC-10 |
| DASH-023 | Configure Actuator, structured logging, MDC X-Correlation-Id | P7 | S | SPEC-07 |
| DASH-024 | Implement Cucumber BDD tests (3 features) | P8 | L | SPEC-08 |

---

## Issues de integracao — lista completa

| # | Titulo | P | Size |
|---|---|---|---|
| INT-DASH-001 | EVNT -> DASH dispatch sink: contract + SVC_EVNT auth | P2 | M |
| INT-DASH-002 | DASH -> EVNT callbacks: delivered/acknowledged idempotency | P2 | M |
| INT-DASH-003 | DASH -> PAC calibration write-through: relay ProblemDetail | P4 | S |
| INT-DASH-004 | DASH -> HIST history: pagination forwarding, size cap=100, hard-503 | P3 | S |
| INT-DASH-005 | DASH -> EDGE liveness: conditional GET ETag/304 + staleness-to-unknown | P3 | M |
| INT-DASH-006 | DASH -> ACS scope resolution: cache TTL=30s + JWK-set + hard-503 | P5 | M |
| INT-DASH-007 | GWY routing: /panel/** publico, /internal/** bloqueado na borda | P6 | S |
