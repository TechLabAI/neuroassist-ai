# Architecture Decision Records — DASH (dash-service, local)

Service-level decisions for `dash-service` (module **DASH — Dashboard e Notificações**). These build on the
shared, project-level ADRs and record only what is specific to this service.

## Index (local)

| ADR | Title | Status |
|---|---|---|
| ADR-004 | Backend-for-Frontend, not a system of record | aceita |
| ADR-005 | Real-time panel delivery via server-push (SSE), broker-deferred | aceita |
| ADR-006 | Write-through to owner services (no local authoring) | aceita |

## Shared ADRs inherited (see `../../adr/`)

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Hexagonal Architecture | aceita |
| ADR-002 | Independent Microservices | aceita |
| ADR-003 | Multi-tenancy (schema-per-tenant) | aceita |
| ADR-007 | Edge device authentication | aceita |

> Local numbering continues past the shared set (001–003) to keep IDs unambiguous across the two
> scopes — a local ADR is never confused with a shared one.

## Relation map
- **ADR-004** applies **ADR-001** (hexagonal) and **ADR-002** (independent microservices): dash-service is
  its own hexagonal microservice, but deliberately a thin Backend-for-Frontend that owns no operational
  aggregate — the read/write truth stays in EVNT, PAC, ACS, HIST and EDGE.
- **ADR-005** depends on **ADR-004** and honors the project-wide messaging-disabled posture: EVNT→DASH
  dispatch and DASH→EVNT callbacks are synchronous REST, and the caregiver's browser receives notifications
  over a server-push SSE channel; the broker is deferred, not designed away. It closes the delivery half of
  EVNT ADR-005 (urgency-tiered notification).
- **ADR-006** strengthens **ADR-004**: caregiver writes (calibration, confirmation, acknowledgement) are
  proxied to their owners (PAC ADR-004, EVNT ADR-006) so DASH never becomes a second source of truth.
- **ADR-003** (multi-tenancy) scopes every composed view and every proxied write per empresa; **ADR-007**
  (edge auth) is inherited context — DASH renders only derived EDGE liveness, never device credentials.

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. Numbers are stable and never
renumbered; a revoked decision keeps its number so cross-references survive.
