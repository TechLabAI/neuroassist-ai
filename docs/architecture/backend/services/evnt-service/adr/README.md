# Architecture Decision Records — EVNT (evnt-service, local)

Service-level decisions for `evnt-service` (module **EVNT — Eventos e Alarmes**). These build on the
shared, project-level ADRs and record only what is specific to this service.

## Index (local)

| ADR | Title | Status |
|---|---|---|
| ADR-004 | Evento as system of record (idempotent ingress) | aceita |
| ADR-005 | Urgency-tiered notification orchestration (synchronous, broker-deferred) | aceita |
| ADR-006 | Caregiver confirmation & false-positive feedback (terminal, audited) | aceita |

## Shared ADRs inherited (see `../../adr/`)

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Hexagonal Architecture | aceita |
| ADR-002 | Independent Microservices | aceita |
| ADR-003 | Multi-tenancy (schema-per-tenant) | aceita |

> Local numbering continues past the shared set (001–003) to keep IDs unambiguous across the two
> scopes — a local ADR is never confused with a shared one.

## Relation map
- **ADR-004** applies **ADR-001** (hexagonal) and **ADR-002** (independent microservices): evnt-service is
  its own hexagonal microservice and the system of record for `Evento`; GEST validates but does not persist.
- **ADR-005** is the domain-critical decision of this service and depends on **ADR-004** — the Evento must
  be durable before the urgency-tiered notification is dispatched, so an emergency is never lost.
- **ADR-006** depends on **ADR-004** — the feedback verdict is a terminal, audited transition on the
  persisted Evento, and feeds the false-positive signal used downstream (PAC/GEST) for tuning.
- **ADR-003** (multi-tenancy) scopes every Evento, dispatch and lookup per empresa.

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. Numbers are stable and never
renumbered; a revoked decision keeps its number so cross-references survive.
