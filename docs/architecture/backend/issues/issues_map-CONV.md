# Issues Map — CONV (conv-service)

> Gerado em 2026-08-31 | 22 issues funcionais + 6 issues de integração = 28 total
> Serviço: `conv-service` | Stack: Java 21 / Spring Boot 3.5.x
> ADR hexagonal: `ADR-001-hexagonal-architecture.md`

---

## Por prioridade

| Issue | Título | Tipo | Prioridade | Estimativa |
|---|---|---|---|---|
| CONV-001 | Criar estrutura Gradle + pacotes hexagonais do conv-service | Funcional | P1 | M |
| CONV-002 | Criar entidades JPA — ArvoreFala, NoArvoreFala, NoConteudo, AuditOutbox | Funcional | P1 | M |
| CONV-003 | Criar DTOs de API (request e response para os 4 endpoints públicos) | Funcional | P1 | M |
| CONV-004 | Declarar ports do domínio (SearchPort, AudioRefPort, AcsSessionPort, VocabularyPort, HistAuditPort) | Funcional | P1 | S |
| INT-CONV-004 | Decisão — endpoints de Proposals (Tela 09) pertencem a TREI, não a CONV | Integração | P2 | S |
| CONV-005 | Criar ConvTreeService — navigateTree (GET /tree, locale resolution, pending_locale) | Funcional | P2 | L |
| CONV-006 | Criar NodeSelectionService — selectNode (POST /select, SpeechNodeSelected, outbox) | Funcional | P2 | L |
| CONV-007 | Criar BindingResolutionService — resolveBinding (engineFeatureRef opaco) | Funcional | P2 | S |
| CONV-008 | Criar Flyway migrations V0001-V0003 — shipped para MIG | Funcional | P3 | M |
| CONV-009 | Criar JPA repositories e adapters de persistência | Funcional | P3 | M |
| CONV-010 | Criar AudioRefPortAdapter (filesystem, CONV_AUDIO_ROOT) | Funcional | P3 | S |
| CONV-011 | Criar AcsSessionAdapter — Feign para acs-service (blacklist por request) | Funcional | P4 | M |
| CONV-012 | Criar VocabularyAdapter — Feign para pac-service (client_credentials) | Funcional | P4 | M |
| CONV-013 | Criar HistAuditAdapter — Feign para hist-service (best-effort, outbox) | Funcional | P4 | M |
| CONV-014 | Criar SemanticSearchService — searchAssisted (kNN via SearchPort, fail-open) | Funcional | P4 | L |
| INT-CONV-001 | GAP G4 — Confirmar contrato VocabularyPort com PAC/GEST (engineFeatureRef) | Integração | P4 | M |
| INT-CONV-002 | GAP G5 — Contrato CONV↔TREI (índice arvore-fala, model_id, path áudio) | Integração | P4 | M |
| INT-CONV-003 | Contrato CONV↔HIST — HistAuditPort REST (endpoint ingest, schema fatos) | Integração | P4 | S |
| INT-CONV-005 | Contrato operacional SearchPort model_id (risco R5 CONV↔TREI) | Integração | P4 | S |
| CONV-015 | Configurar OAuth2 Resource Server (jwk-set-uri, escopos, rejeição caregiver) | Funcional | P5 | M |
| CONV-016 | Implementar guards: anti-biométrico (400) + locale (pending_locale 409) | Funcional | P5 | M |
| INT-CONV-006 | Provisionamento client_credentials CONV→PAC/GEST (pac.vocabulary.read) | Integração | P5 | S |
| CONV-017 | Criar OutboxRetrySweepService (@Scheduled, retry budget) | Funcional | P6 | M |
| CONV-018 | Criar GET /health + ConvServedDegraded + métricas Micrometer | Funcional | P7 | M |
| CONV-019 | Criar CucumberSpringContext + SharedSteps + AssistedSearchSteps (D-492) | Funcional | P8 | M |
| CONV-020 | Criar step definitions para 4 features (tree, selection, session, binding) | Funcional | P8 | L |
| CONV-021 | Criar outbox-retry-sweep.feature (gap BDD SPEC-09) | Funcional | P9 | S |
| CONV-022 | Criar ADR spike — cache blacklist ACS (risco R3, SPEC-06) | Funcional | P9 | S |

---

## Por SPEC

| SPEC | Issues cobertas |
|---|---|
| SPEC-01 | CONV-001, CONV-003, CONV-004, CONV-007, INT-CONV-004 |
| SPEC-02 | CONV-005, CONV-006 |
| SPEC-03 | CONV-014, INT-CONV-002, INT-CONV-005 |
| SPEC-04 | CONV-002, CONV-008, CONV-009 |
| SPEC-05 | CONV-006 |
| SPEC-06 | CONV-011, CONV-015, CONV-016, CONV-022, INT-CONV-006 |
| SPEC-07 | CONV-018 |
| SPEC-08 | CONV-019, CONV-020 |
| SPEC-09 | CONV-006, CONV-013, CONV-017, CONV-021, INT-CONV-003 |
| SPEC-10 | CONV-004, CONV-007, CONV-010, CONV-012, INT-CONV-001, INT-CONV-002 |
| SPEC-11 | CONV-005, CONV-016 |
| SPEC-12 | CONV-010, CONV-014 |

---

## Por camada hexagonal

| Camada | Issues |
|---|---|
| `api/` (controllers, DTOs, guards) | CONV-003, CONV-005, CONV-006, CONV-007, CONV-014, CONV-015, CONV-016, CONV-018 |
| `application/` (services) | CONV-005, CONV-006, CONV-007, CONV-014, CONV-017, CONV-018 |
| `domain/` (entities, ports, exceptions) | CONV-002, CONV-004 |
| `infrastructure/` (adapters, repositories, security) | CONV-008, CONV-009, CONV-010, CONV-011, CONV-012, CONV-013, CONV-015 |
| `test/` (BDD, stubs) | CONV-019, CONV-020, CONV-021 |
| `docs/adr/` | CONV-022 |
| `contracts/` (atualização cross-module) | INT-CONV-001, INT-CONV-002, INT-CONV-003, INT-CONV-004, INT-CONV-005, INT-CONV-006 |

---

## Gaps do input do autopilot → issues geradas

| Gap | Issue | Decisão |
|---|---|---|
| GAP G4 (VocabularyPort/engineFeatureRef) | INT-CONV-001 | Investigar e confirmar formato real |
| GAP G5 (CONV↔TREI índice arvore-fala) | INT-CONV-002 | Formalizar contrato de dados; bloqueia produção |
| GAP CRÍTICO/HIGH FE (Proposals Tela 09) | INT-CONV-004 | Pertence ao TREI; CONV não adiciona endpoints |
| Step Gherkin D-492 (no intent text) | CONV-019 | AssistedSearchSteps com step descritivo |
| Outbox sweep sem feature BDD (SPEC-09) | CONV-021 | outbox-retry-sweep.feature criada |
| Cache blacklist ACS (risco R3) | CONV-022 | ADR/spike antes do scaffold |
| SearchPort model_id alignment (risco R5) | INT-CONV-005 | Contrato operacional + env obrigatória |
| client_credentials VocabularyPort (G7) | INT-CONV-006 | Provisionamento Keycloak — concern ACS |
