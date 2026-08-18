---
module: DASH
service: dash-service
package: com.neuroassist.dash
version: "1.0.0"
status: specs-complete
port: 8090
adrs:
  - ADR-004-backend-for-frontend-not-system-of-record.md
  - ADR-005-realtime-panel-delivery-sse-broker-deferred.md
  - ADR-006-write-through-to-owner-services.md
generatedAt: "2026-08-17"
---

# dash-service Spec Entry Point

> Module: **DASH — Dashboard e Notificações** · Service: `dash-service`
> Package: `com.neuroassist.dash` · Multi-tenant: schema-per-empresa (caregiver-scoped, owns no tenant tables)
> Messaging: disabled (synchronous REST + browser SSE) · AI: none (`ai.hasAi = false`)
> Pipeline phase: **specs** complete (contracts + SPECs + Gherkin features + C4 diagrams)

## Purpose

`dash-service` is the **Backend-for-Frontend (BFF)** of the caregiver web panel. It gives one human — the
caregiver — a single, caregiver-scoped boundary that:

- **Composes** the panel's screens by fanning out to five owner services (HIST history/audit, PAC
  patient + command catalog + calibration, EVNT event/alarm state, EDGE device liveness, ACS identity +
  caregiver↔patient scope) and returning **panel-shaped view models**, never raw upstream payloads.
- **Delivers** EVNT's notifications to the caregiver's browser in real time over a **Server-Sent Events
  (SSE)** channel, and reports `delivered` / `acknowledged` back to EVNT.
- **Replays** unacknowledged notifications on (re)connect so nothing is lost across a disconnect — DASH
  keeps no durable notification inbox.
- **Proxies** caregiver actions write-through to their owners (calibration → PAC, confirmation/ack → EVNT),
  never becoming a second source of truth.

DASH **owns no domain persistence** (ADR-004): the only runtime "state" is an **in-memory, ephemeral SSE
session registry**. Every business fact is read live from, or written through to, an owner service. A DASH
restart loses no domain data. DASH never touches biometric, image or facial-landmark data (project-wide
LGPD hard rule).

## Document map

| Document | Path | Content |
|---|---|---|
| API contract | `contracts/api-contract.md` | Panel REST surface, SSE stream, internal dispatch sink, ProblemDetail, auth |
| Data model | `contracts/data-model.md` | **Zero MVP tables**; in-memory session registry; optional deferred preference table |
| Event contracts | `contracts/event-contracts.md` | Internal delivery semantics (no broker); EVNT dispatch/callback contracts |
| Hub integration | `contracts/hub-integration.md` | TechLab AI / hub dependency = **none** (deterministic, inference-free) |
| Integration contracts | `contracts/integration-contracts.md` | Upstream REST ports: HIST, PAC, EVNT, EDGE, ACS (timeouts, degradation, retry) |
| Event catalog | `events/event-catalog.md` | Delivery/session signals produced + notification dispatch consumed |
| SPEC-01 | `spec/backend/SPEC-01-api-surface.md` | Panel REST + SSE surface |
| SPEC-02 | `spec/backend/SPEC-02-panel-composition-bff.md` | Core: parallel fan-out composition + per-section degradation |
| SPEC-03 | `spec/backend/SPEC-03-notification-delivery-sse.md` | Sub-feature: SSE delivery, catch-up, delivery/ack callbacks |
| SPEC-04 | `spec/backend/SPEC-04-data-model-migrations.md` | Zero-table posture + optional deferred preference |
| SPEC-05 | `spec/backend/SPEC-05-idempotency-concurrency.md` | `dispatchId` idempotency, session concurrency, fail-open |
| SPEC-06 | `spec/backend/SPEC-06-security.md` | OAuth2 caregiver auth, service-to-service auth, caregiver scope |
| SPEC-07 | `spec/backend/SPEC-07-observability-health.md` | Health, metrics, correlation, SSE liveness |
| SPEC-08 | `spec/backend/SPEC-08-bdd-test-scenarios.md` | BDD narrative + reusable steps |
| SPEC-09 | `spec/backend/SPEC-09-messaging-architecture.md` | Messaging disabled; SSE vs broker; broker-deferred path |
| SPEC-10 | `spec/backend/SPEC-10-integrations.md` | Consolidated upstream integration matrix |
| Diagrams | `diagrams/c4-{context,container,component}.puml` | C4 model |
| Setup | `setup-local.md` | Port 8090, env vars, run/test |

## Architectural anchors (from vision)

- **ADR-004 (local)** — BFF, not a system of record: DASH persists no operational aggregate.
- **ADR-005 (local)** — real-time panel delivery via SSE; broker fan-out deferred project-wide.
- **ADR-006 (local)** — caregiver writes are proxied write-through to the owner service.
- **ADR-002 (shared)** — independent microservices, REST via GWY, discovery via REG (Eureka).
- **ADR-003 (shared)** — schema-per-`empresa`; DASH holds no tenant tables in the MVP.

## Traceability

Every acceptance criterion in the SPECs and every endpoint invariant in `api-contract.md` uses **EARS**
notation (`docs/architecture/backend/standards/ears-notation.md`) so they feed the Cucumber generator and
controller-annotation derivation mechanically.
