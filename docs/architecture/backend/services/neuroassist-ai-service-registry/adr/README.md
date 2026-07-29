# ADR Index — REG (Service Registry)

Architecture Decision Records local to the `neuroassist-ai-service-registry` service.
Project-wide decisions (hexagonal architecture, independent microservices, multi-tenancy) live
in `docs/architecture/backend/adr/` and are referenced, not duplicated, here.

## Índice (local)

| ADR | Título | Status |
|---|---|---|
| ADR-001 | Netflix Eureka as the Service Registry | aceita |
| ADR-002 | Standalone registry (no HA cluster) for the MVP | aceita |

## Referências (project-level, shared)

| ADR | Título | Local |
|---|---|---|
| ADR-001 (shared) | Hexagonal Architecture | `docs/architecture/backend/adr/` |
| ADR-002 (shared) | Independent Microservices | `docs/architecture/backend/adr/` |
| ADR-003 (shared) | Multi-tenancy | `docs/architecture/backend/adr/` |

> Note: REG does not follow hexagonal layering and is outside the multi-tenancy
> model (it is stateless platform infrastructure). The shared ADRs govern the
> business services; REG cites them for context only.

## Mapa de Relações
- ADR-001 (Eureka choice) is the foundation; ADR-002 (standalone) refines the
  deployment topology of that choice.

## Processo
Status values: `proposta` | `aceita` | `substituída por ADR-NNN`. New local
decisions are appended here with the next sequential id; superseded ADRs keep their
number and are struck through in this index.
