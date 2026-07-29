---
module: EVNT
name: Eventos e Alarmes
service: evnt-service
package: com.neuroassist
project: NeuroAssist AI
stage: specs
architecture: independent microservices (ADR-002 shared)
messaging: disabled
ai: none (ai.hasAi = false)
tenancy: schema-per-empresa (ADR-003 shared)
generatedBy: /be-architect-specs neuroassist-ai EVNT
generatedAt: 2026-07-28
---

# EVNT — Service Spec (entry point / map)

`evnt-service` is the **event and alarm authority** of NeuroAssist AI: it turns the edge's validated
`RecognizedCommandReport` into a persisted, structured `Evento` (system of record), dispatches an
urgency-tiered notification (full-screen alarm for `emergencia`, discreet for `rotina`), and closes the
loop with a terminal, audited caregiver verdict (`real` | `falso_positivo`). No broker, no AI.

## Contracts
- [`contracts/api-contract.md`](contracts/api-contract.md) — REST + **SSE live channel** (closes FE ADR-005)
- [`contracts/data-model.md`](contracts/data-model.md) — canonical DDL (`evento`, `notification_dispatch`, `report_idempotency`)
- [`contracts/event-contracts.md`](contracts/event-contracts.md) — in-process domain events + SSE frames
- [`contracts/hub-integration.md`](contracts/hub-integration.md) — inter-module Feign (GEST/PAC/ACS/DASH/HIST); TechLab AI: none
- [`contracts/integration-contracts.md`](contracts/integration-contracts.md) — edge ingress + external

## SPECs
| SPEC | Tema |
|---|---|
| [SPEC-01](spec/backend/SPEC-01-api-surface.md) | API surface |
| [SPEC-02](spec/backend/SPEC-02-notification-orchestration.md) | Notification orchestration (core) |
| [SPEC-03](spec/backend/SPEC-03-live-event-delivery.md) | **Live event delivery (SSE + poll)** — closes FE ADR-005 |
| [SPEC-04](spec/backend/SPEC-04-data-model-migrations.md) | Data model + migrations |
| [SPEC-05](spec/backend/SPEC-05-idempotency-concurrency.md) | Idempotency + concurrency |
| [SPEC-06](spec/backend/SPEC-06-security.md) | Security |
| [SPEC-07](spec/backend/SPEC-07-observability-health.md) | Observability + health |
| [SPEC-08](spec/backend/SPEC-08-bdd-test-scenarios.md) | BDD test scenarios |
| [SPEC-09](spec/backend/SPEC-09-messaging-architecture.md) | Messaging architecture (broker absent by design) |
| [SPEC-10](spec/backend/SPEC-10-integrations.md) | Integrations |

## Tests (Gherkin)
- [`tests/cucumber/report-ingress-idempotency.feature`](tests/cucumber/report-ingress-idempotency.feature)
- [`tests/cucumber/urgency-tiered-notification.feature`](tests/cucumber/urgency-tiered-notification.feature)
- [`tests/cucumber/caregiver-confirmation-feedback.feature`](tests/cucumber/caregiver-confirmation-feedback.feature)
- [`tests/cucumber/live-event-delivery.feature`](tests/cucumber/live-event-delivery.feature)

## Diagrams (C4)
- [`diagrams/c4-context.puml`](diagrams/c4-context.puml) · [`diagrams/c4-container.puml`](diagrams/c4-container.puml) · [`diagrams/c4-component.puml`](diagrams/c4-component.puml)

## Vision source (be-architect-vision, prerequisite)
`_brief.md`, `01..06-*.md`, `adr/ADR-004..006`.

## Decisions closed / carried
- **Closed:** live-delivery channel shape — **SSE `GET /events/stream` (primary) + `GET /events/active`
  (hydration + polling fallback)** — resolves the M1 handoff open item and unblocks FE ADR-005.
- **Carried to Backlog (open):** device-token auth shape with EDGE (doc 06 gap #1); emergency escalation via
  ACS (gap #3); false-positive-rate consumer contract (gap #5). See SPEC-06 / SPEC-10.

## Next
→ `/be-architect-backlog neuroassist-ai EVNT`
