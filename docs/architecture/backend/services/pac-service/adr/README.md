# Architecture Decision Records — PAC (pac-service, local)

Service-level decisions for `pac-service` (module **PAC — Pacientes e Catálogo de Comandos**). These
build on the shared, project-level ADRs and record only what is specific to this service.

## Index (local)

| ADR | Title | Status |
|---|---|---|
| ADR-004 | PAC as authoring source of truth for command calibration (GEST snapshots) | aceita |
| ADR-005 | Emergency-command eligibility gate (synchronous, fail-closed against GEST) | aceita |
| ADR-006 | Command edit as versioned/retire (history-preserving, no silent mutation) | aceita |

## Shared ADRs inherited (see `../../adr/`)

| ADR | Title | Status |
|---|---|---|
| ADR-001 | Hexagonal Architecture | aceita |
| ADR-002 | Independent Microservices | aceita |
| ADR-003 | Multi-tenancy (schema-per-tenant) | aceita |

> Local numbering continues past the shared set (001–003) to keep IDs unambiguous across the two
> scopes — a local ADR is never confused with a shared one.

## Relation map
- **ADR-004** applies **ADR-001** (hexagonal) and **ADR-002** (independent microservices): pac-service is
  its own hexagonal microservice and the authoring system of record for `Comando` calibration; GEST reads
  and snapshots that calibration but does not author it.
- **ADR-005** is the safety-critical decision of this service and depends on **ADR-004** — an emergency
  command's calibration is only meaningful if its primitive is genuinely detectable, so publishing an
  emergency requires a synchronous, fail-closed detectability check against GEST.
- **ADR-006** depends on **ADR-004** — because EVNT/HIST snapshot the `Comando` at event time, a
  history-referenced command must be versioned/retired rather than silently mutated, keeping past events
  interpretable.
- **ADR-003** (multi-tenancy) scopes every `Paciente`, `Comando` and calibration per empresa.

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. Numbers are stable and never
renumbered; a revoked decision keeps its number so cross-references survive.
