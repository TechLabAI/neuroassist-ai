# ADRs — neuroassist-ai-migrations (MIG)

Architecture Decision Records for the centralized Migrations service. Local (service-level) ADRs
live here; project-wide decisions are referenced from the shared set.

## Local ADRs (this service)

| ADR | Title | Status |
|---|---|---|
| ADR-002 | Centralized Flyway across all schemas | aceita |
| ADR-005 | Tenant schema provisioning strategy | aceita |

## Shared ADRs (project-level — see `docs/architecture/backend/adr/`)

MIG inherits and must comply with the project-wide decisions:

| ADR | Title | Relevance to MIG |
|---|---|---|
| ADR-001 | Hexagonal Architecture | MIG follows api/application/domain/infrastructure even as infra service |
| ADR-002 (shared) | Independent Microservices | Platform composition stance MIG operates within |
| ADR-003 (shared) | Multi-tenancy (schema-per-tenant) | The isolation model MIG enforces structurally |

> Note: the shared set and the local set both use the number `002` in their own namespaces.
> Local `ADR-002` = *centralized Flyway* (service boundary); shared `ADR-002` = *independent
> microservices* (project). They are distinct documents in distinct folders.

## Relation map
- Local ADR-002 (centralized Flyway) depends on shared ADR-003 (schema-per-tenant): centralization
  exists to serve the tenant-isolation model.
- Local ADR-005 (provisioning) refines local ADR-002 for the per-tenant lifecycle.

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. A revoked decision keeps its
number and is struck through; new decisions get the next free local number.
