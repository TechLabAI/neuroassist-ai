# Architecture Decision Records — GEST (gest-service, local)

Service-level decisions for `gest-service` (module **GEST — Detecção de Gesto (Edge)**). These
build on the shared, project-level ADRs and record only what is specific to this service.

## Index (local)

| ADR | Title | Status |
|---|---|---|
| ADR-005 | Edge/Cloud Detection Split (gest-service boundary) | aceita |
| ADR-006 | TipoGesto Detectability Lifecycle | aceita |

## Shared ADRs inherited (see `../../adr/`)

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Hexagonal Architecture | aceita |
| ADR-002 | Independent Microservices | aceita |
| ADR-003 | Multi-tenancy (schema-per-tenant) | aceita |

> Local numbering continues past the shared set (001–003) to keep IDs unambiguous across the two
> scopes — a local ADR is never confused with a shared one.

## Relation map
- **ADR-005** applies **ADR-001** (hexagonal) and **ADR-002** (independent microservices):
  gest-service is its own hexagonal microservice; the only further decomposition is cloud↔edge
  (out of process, not backend↔backend).
- **ADR-006** is the domain-critical decision of this service and depends on **ADR-005** — the
  detectability lifecycle governs which primitives enter the detection profile that the edge
  (per ADR-005) pulls.
- **ADR-003** (multi-tenancy) scopes the TipoGesto catalog and profiles per empresa.

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. Numbers are stable and never
renumbered; a revoked decision keeps its number so cross-references survive.
