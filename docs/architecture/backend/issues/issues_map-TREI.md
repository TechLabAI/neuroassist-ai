# Issues Map — TREI (trei-service)

> Gerado em 2026-09-01 | 22 issues funcionais + 5 issues de integração = 27 total
> Serviço: `trei-service` | Stack: Java 21 / Spring Boot 3.5.x
> ADR hexagonal: `docs/architecture/backend/adr/ADR-*-hexagonal-architecture.md`

---

## Por prioridade

| Issue | Título | Tipo | Prioridade | Estimativa |
|---|---|---|---|---|
| TREI-001 | Domain entities + JPA mappings (TREI-private tables) | Funcional | P1 | M |
| TREI-002 | Flyway migrations V0201-V0203 (shipped to MIG) | Funcional | P1 | M |
| TREI-003 | Hexagonal port interfaces (9 outbound ports) | Funcional | P1 | S |
| TREI-004 | REST DTOs + API controllers (9 endpoints, RFC 7807) | Funcional | P1 | L |
| TREI-005 | SessaoTreinamentoService — startSession + requestProposal (Anthropic) | Funcional | P2 | L |
| TREI-006 | Dual authorization gate — approveCaregiver + approvePatient (ADR-003) | Funcional | P2 | L |
| TREI-007 | NoAutoriaService — materializeNode + discardNode (ADR-018) | Funcional | P2 | L |
| TREI-008 | JPA repositories + adapters (TREI-private tables) | Funcional | P3 | M |
| TREI-009 | JPA write adapters (shared CONV tables — conv_no_arvore_fala + conv_no_conteudo) | Funcional | P3 | M |
| TREI-010 | LlmAuthoringPort adapter — AnthropicAdapter (TECH-007, SEC-002) | Funcional | P4 | M |
| TREI-011 | EmbeddingPort adapter — EmbeddingSidecarAdapter + G5ModelIdGuard (SPEC-11) | Funcional | P4 | M |
| TREI-012 | TtsSynthesisPort + ElevenLabsAdapter + AudioStorePort (idempotent, FIN-002) | Funcional | P4 | M |
| TREI-013 | IndexPort adapter — ElasticsearchIndexAdapter (8-field traceability) | Funcional | P4 | M |
| INT-TREI-002 | INT: TREI -> PAC (patient resolution, opaque engine_feature_ref) | Integração | P4 | M |
| INT-TREI-003 | INT: TREI -> HIST audit (5 facts, no clinical text, buffered) | Integração | P4 | M |
| INT-TREI-004 | INT: TREI -> ES IndexPort (embed + index, G5, 8-field traceability) | Integração | P4 | M |
| TREI-014 | OAuth2 Resource Server + schema-per-empresa routing (D-492 pitfall) | Funcional | P5 | M |
| TREI-015 | ACS revocation + dual-actor scope enforcement (ADR-003/ADR-012) | Funcional | P5 | M |
| INT-TREI-001 | INT: TREI -> ACS (revocation blacklist, fail-closed, dual-actor) | Integração | P5 | M |
| TREI-016 | PublicacaoService + OutboxDrainer (embed->tts->index, G5 gate, ADR-004) | Funcional | P6 | L |
| TREI-017 | Idempotency guards (SPEC-05: no-op re-approval, TTS reuse, outbox UNIQUE) | Funcional | P6 | S |
| INT-TREI-005 | INT: TREI <-> CONV shared state (conv writes, audio store G6, flip ativo) | Integração | P6 | M |
| TREI-018 | Health endpoint GET /health (6-dependency status, embedModelId G5 surface) | Funcional | P7 | M |
| TREI-019 | Structured logging + X-Correlation-Id propagation (MDC, all downstream) | Funcional | P7 | S |
| TREI-020 | Cucumber BDD: dual-auth-gate + llm-authoring-proposal features | Funcional | P8 | L |
| TREI-021 | Cucumber BDD: publication-pipeline + bilingual-locale + G5-parity features | Funcional | P8 | L |
| TREI-022 | Outbox retry backoff + circuit-breakers LLM/TTS + outbox-falha alert | Funcional | P9 | M |

---

## Por SPEC

| SPEC | Issues cobertas |
|---|---|
| SPEC-01 (api-surface) | TREI-003, TREI-004, TREI-007, TREI-018 |
| SPEC-02 (authoring-orchestration) | TREI-005, TREI-010 |
| SPEC-03 (dual-authorization-gate) | TREI-006, TREI-017, INT-TREI-001 |
| SPEC-04 (data-model-migrations) | TREI-001, TREI-002, TREI-008, TREI-009 |
| SPEC-05 (idempotency-concurrency) | TREI-017, TREI-012 |
| SPEC-06 (security) | TREI-014, TREI-015, INT-TREI-001 |
| SPEC-07 (observability-health) | TREI-018, TREI-019, TREI-022 |
| SPEC-08 (bdd-test-scenarios) | TREI-020, TREI-021 |
| SPEC-09 (messaging-architecture) | TREI-022 |
| SPEC-10 (integrations) | TREI-010, TREI-011, TREI-012, TREI-013 |
| SPEC-11 (embedding-model-parity-g5) | TREI-011, INT-TREI-004, TREI-021 |
| SPEC-12 (publication-pipeline-outbox) | TREI-016, TREI-013, INT-TREI-004, INT-TREI-005 |

---

## Por camada hexagonal

| Camada | Issues |
|---|---|
| `domain` (entidades, enums, ports, eventos) | TREI-001, TREI-003 |
| `application` (services, orchestration) | TREI-005, TREI-006, TREI-007, TREI-016, TREI-017 |
| `api` (controllers, DTOs, error handler) | TREI-004, TREI-018 |
| `infrastructure.persistence` | TREI-008, TREI-009 |
| `infrastructure.llm` | TREI-010 |
| `infrastructure.embedding` | TREI-011 |
| `infrastructure.tts + audiostore` | TREI-012 |
| `infrastructure.elasticsearch` | TREI-013 |
| `infrastructure.security` | TREI-014, TREI-015 |
| `infrastructure.outbox` | TREI-016, TREI-022 |
| `infrastructure.logging` | TREI-019 |
| `migrations` | TREI-002 |
| `test.bdd` | TREI-020, TREI-021 |
| integrações externas | INT-TREI-001, INT-TREI-002, INT-TREI-003, INT-TREI-004, INT-TREI-005 |

---

## ADRs de referência (TREI-service)

| ADR | Decisão | Issues impactadas |
|---|---|---|
| ADR-001 (TREI) | TREI é serviço novo independente | TREI-001..TREI-022 |
| ADR-002 (TREI) | Autoria assistida por LLM fora do runtime | TREI-005, TREI-010 |
| ADR-003 (TREI) | Dupla autorização como gate de publicação | TREI-006, TREI-014, TREI-015, INT-TREI-001 |
| ADR-004 (TREI) | Pipeline broker-less com outbox | TREI-016, TREI-017 |
| ADR-005 (TREI) | Autoria bilíngue por locale | TREI-007, TREI-021 |
| ADR-018 (projeto) | Bilinguismo EN/PT, locale-invariant structure | TREI-007, TREI-021 |
| ADR-016 (projeto) | TREI off-runtime path do CONV | TREI-018 |
