---
service: trei-service
module: TREI
displayName: Treinamento Assistido por IA (Autoria da Árvore de Falas)
stack: java-21-spring-boot
packageBase: com.neuroassist.trei
publicBasePath: /api/v1/trei
role: authoring plane (write side) of the CONV speech tree
messaging: disabled
elastic: writer of neuroassist-ai_arvore-fala (IndexPort)
sharedContract: CONV↔TREI (data-model §3 G5, index-design-arvore-fala)
generatedBy: /be-architect-specs
---

# TREI — Service Spec (`trei-service`)

TREI is the **authoring plane** of the NeuroAssist AI speech tree — the single, supervised place where the
content CONV serves at runtime is created and grown. An LLM (Claude/Anthropic) proposes category/phrase
text; only content **approved by both the caregiver and the patient** (dual authorization, ADR-003) is
embedded, voiced in the patient's cloned voice (ElevenLabs, ADR-004→ADR-017), and indexed into the shared
`arvore-fala` Elasticsearch index (ADR-004→ADR-015). **TREI writes; CONV reads.** All generation is
quarantined here, off the patient's daily communication path.

## Contract map

| Area | File |
|---|---|
| API surface (REST, RFC 7807, auth, dual-auth endpoints) | [`contracts/api-contract.md`](contracts/api-contract.md) |
| Data model (TREI-private tables + **G5 parity §3**) | [`contracts/data-model.md`](contracts/data-model.md) |
| Events (in-process, broker-less outbox pipeline, HIST audit) | [`contracts/event-contracts.md`](contracts/event-contracts.md) |
| Intra-mesh (ACS/PAC/HIST/GWY/REG; TechLab AI = none) | [`contracts/hub-integration.md`](contracts/hub-integration.md) |
| External (Anthropic, embedding sidecar, ElevenLabs, ES, audio FS) | [`contracts/integration-contracts.md`](contracts/integration-contracts.md) |
| Event catalog | [`events/event-catalog.md`](events/event-catalog.md) |
| Local setup (ports, env, run) | [`setup-local.md`](setup-local.md) |
| BDD features (Gherkin) | [`tests/cucumber/`](tests/cucumber/) |
| SPECs | [`spec/backend/`](spec/backend/) |
| C4 diagrams | [`diagrams/`](diagrams/) |

## SPEC index

| SPEC | Theme |
|---|---|
| [SPEC-01](spec/backend/SPEC-01-api-surface.md) | API surface |
| [SPEC-02](spec/backend/SPEC-02-authoring-orchestration.md) | Authoring session orchestration (LLM proposals) |
| [SPEC-03](spec/backend/SPEC-03-dual-authorization-gate.md) | Dual-authorization gate (core sub-feature) |
| [SPEC-04](spec/backend/SPEC-04-data-model-migrations.md) | Data model + migrations |
| [SPEC-05](spec/backend/SPEC-05-idempotency-concurrency.md) | Idempotency + concurrency |
| [SPEC-06](spec/backend/SPEC-06-security.md) | Security (OAuth2, two actors, revocation) |
| [SPEC-07](spec/backend/SPEC-07-observability-health.md) | Observability + health |
| [SPEC-08](spec/backend/SPEC-08-bdd-test-scenarios.md) | BDD test scenarios |
| [SPEC-09](spec/backend/SPEC-09-messaging-architecture.md) | Messaging (broker-less publication pipeline) |
| [SPEC-10](spec/backend/SPEC-10-integrations.md) | Integrations |
| [SPEC-11](spec/backend/SPEC-11-embedding-model-parity-g5.md) | Embedding model-id parity (G5) |
| [SPEC-12](spec/backend/SPEC-12-publication-pipeline-outbox.md) | Publication pipeline (embed→tts→index, outbox) |

## Key decisions carried in (vision → specs)

- **New independent microservice** (ADR-001 local, shared hexagonal `ADR-001-hexagonal-architecture.md`).
- **LLM authoring off-runtime** (ADR-002 local / ADR-016): assisted generation only in TREI's session.
- **Dual authorization is a domain invariant** (ADR-003): caregiver + patient, async, different shells.
- **Publication pipeline = embed + TTS + index, broker-less outbox** (ADR-004): atomic → `ativo`.
- **Bilingual independent authoring per locale** (ADR-005 → ADR-018): structure locale-invariant.
- **G5 model-id parity** fixed in [`contracts/data-model.md`](contracts/data-model.md) §3 and SPEC-11.

## Non-duplication note

TREI **writes into CONV-owned tree tables** (`conv_arvore_fala`, `conv_no_arvore_fala`,
`conv_no_conteudo`) — it does not re-declare them. TREI's own DDL covers only the four authoring-process
tables (`trei_sessao_treinamento`, `trei_proposta_llm`, `trei_conteudo_autoria`,
`trei_publicacao_outbox`). See [`contracts/data-model.md`](contracts/data-model.md) §0.
