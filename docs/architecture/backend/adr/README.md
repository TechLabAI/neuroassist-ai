# Architecture Decision Records — Backend (shared / project-level)

Project-wide backend decisions. These apply to **all** services in the monorepo; individual
services add their own local ADRs under `services/<service>/adr/` and reference this set.

## Index

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Hexagonal Architecture | aceita |
| ADR-002 | Independent Microservices | aceita |
| ADR-003 | Multi-tenancy (schema-per-tenant) | aceita |

## Relation map
- ADR-001 (hexagonal) is the internal organization every service follows, including the
  infrastructure services (GWY, REG, MIG).
- ADR-002 (independent microservices) is the composition stance the modules operate within — each
  module is its own deployable, routed by GWY and discovered via REG.
- ADR-003 (multi-tenancy) is enforced structurally and operationalized by the centralized
  migrations service (MIG) — see `services/neuroassist-ai-migrations/adr/`.

> ADR-002 previously read "Modular Monolith" — an unreviewed auto-generated draft, corrected
> before any scaffold was produced (D-526, techlab-architecture).

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. Numbers are stable and never
renumbered; a revoked decision keeps its number (struck through) so cross-references survive.
