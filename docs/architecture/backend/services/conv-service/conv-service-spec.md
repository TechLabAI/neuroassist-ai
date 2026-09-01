---
service: conv-service
module: CONV
displayName: Conversação por Árvore de Falas
stack: java-21 / spring-boot-3.5.x
package_base: com.neuroassist.conv
public_surface: /api/v1/conv/**
port: 8090
messaging: false
elastic: neuroassist-ai_arvore-fala
status: specs
---

# CONV — Service Spec (`conv-service`)

Entry point and map for the CONV specification layer. CONV is a **new module retrofit** into a
production system: a retrieval-only speech-tree service the *patient-with-session* actor navigates to
communicate, with Elasticsearch kNN assisted search and cached-voice playback. It **reads** what TREI
authors; it never generates content, owns identity, or synthesizes audio.

## Closed decisions (do not re-litigate)

| ADR (local) | Implements (shared, accepted) | Topic |
|---|---|---|
| `adr/ADR-001-conv-servico-novo-independente.md` | ADR-013 | CONV is a new independent service, not a pac-service extension |
| `adr/ADR-002-retrieval-only-elasticsearch-searchport.md` | ADR-015 | Retrieval-only kNN via reused `SearchPort` |
| `adr/ADR-003-identidade-paciente-sessao-consumida-do-acs.md` | ADR-012 | CONV is a resource server; identity owned by ACS |
| `adr/ADR-004-audio-cacheado-por-referencia-e-degradacao.md` | ADR-017 | Cached audio by reference; Web Speech fallback |
| `adr/ADR-005-conteudo-bilingue-por-locale.md` | ADR-018 | Bilingual per-locale content; locale-invariant structure |
| shared `../../adr/ADR-001-hexagonal-architecture.md` (`hexagonal_java`) | — | Hexagonal layering |
| shared `../../adr/ADR-008-public-api-surface-normalization.md` | — | `/api/v1/conv/**` surface |

## Contracts

- [`contracts/api-contract.md`](contracts/api-contract.md) — REST surface, RFC 7807, auth, locale fallback (G3)
- [`contracts/data-model.md`](contracts/data-model.md) — executable SQL DDL, normalized per-locale content (G2)
- [`contracts/event-contracts.md`](contracts/event-contracts.md) — synchronous audit facts to HIST
- [`contracts/hub-integration.md`](contracts/hub-integration.md) — ACS/PAC/GEST/HIST/GWY; TREI boundary (G5)
- [`contracts/integration-contracts.md`](contracts/integration-contracts.md) — Elasticsearch (kNN gap G1 resolved), audio store (G6)
- [`design/index-design-arvore-fala.md`](design/index-design-arvore-fala.md) — shared ES document contract (G5)

## SPECs

| SPEC | Theme |
|---|---|
| [SPEC-01](spec/backend/SPEC-01-api-surface.md) | API surface |
| [SPEC-02](spec/backend/SPEC-02-speech-tree-navigation-and-selection.md) | Core: navigation + node selection |
| [SPEC-03](spec/backend/SPEC-03-assisted-semantic-search.md) | Assisted semantic search (kNN) |
| [SPEC-04](spec/backend/SPEC-04-data-model-migrations.md) | Data model + migrations |
| [SPEC-05](spec/backend/SPEC-05-idempotency-concurrency.md) | Idempotency + concurrency |
| [SPEC-06](spec/backend/SPEC-06-security.md) | Security (resource server, blacklist) |
| [SPEC-07](spec/backend/SPEC-07-observability-health.md) | Observability + health |
| [SPEC-08](spec/backend/SPEC-08-bdd-test-scenarios.md) | BDD test scenarios |
| [SPEC-09](spec/backend/SPEC-09-messaging-architecture.md) | Messaging (disabled) + sync audit + outbox |
| [SPEC-10](spec/backend/SPEC-10-integrations.md) | Integrations (ES, ACS, PAC/GEST, audio, HIST) |
| [SPEC-11](spec/backend/SPEC-11-locale-bilingual-content.md) | Bilingual content + locale fallback (G3) |
| [SPEC-12](spec/backend/SPEC-12-degradation-resilience.md) | Degradation & resilience (OPS-003/OPS-004) |

## Diagrams

`diagrams/c4-context.puml` · `diagrams/c4-container.puml` · `diagrams/c4-component.puml`

## Open gaps carried to backlog / TREI

See [`06-risks-gaps-assumptions.md`](06-risks-gaps-assumptions.md). G1/G2/G3/G5/G6/G7 resolved in specs;
G4 (`engineFeatureRef` format) modeled as opaque reference pending confirmation against the real system.
