# Architecture Decision Records — EDGE (edge-service, local)

Service-level decisions for `edge-service` (module **EDGE — Integração de Dispositivo Edge**). These
build on the shared, project-level ADRs and record only what is specific to this service.

## Index (local)

| ADR | Title | Status |
|---|---|---|
| ADR-005 | Device Pairing and Identity Model | aceita |
| ADR-006 | Heartbeat Liveness and Profile Delivery | aceita |

## Shared ADRs inherited (see `../../adr/`)

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Hexagonal Architecture | aceita |
| ADR-002 | Independent Microservices | aceita |
| ADR-003 | Multi-tenancy (schema-per-tenant) | aceita |

> Local numbering continues past the shared set (001–003) to keep IDs unambiguous across the two
> scopes — a local ADR is never confused with a shared one. Local numbers are per-service; another
> service may use ADR-005/006 for its own decisions.

## Relation map
- **ADR-005** applies **ADR-001** (hexagonal) and **ADR-002** (independent microservices): edge-service
  is its own hexagonal microservice; it does not decompose further and does not run on the device — the
  edge agent is a separate deployable it authenticates and pairs.
- **ADR-006** depends on **ADR-005** (only an authenticated, paired device produces meaningful liveness)
  and consumes GEST's detection-profile version pointer (GEST ADR-005 shared boundary) — EDGE transports
  it; GEST authors it.
- **ADR-003** (multi-tenancy) scopes every device, pairing and heartbeat to one empresa schema.

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. Numbers are stable and never
renumbered; a revoked decision keeps its number so cross-references survive.
