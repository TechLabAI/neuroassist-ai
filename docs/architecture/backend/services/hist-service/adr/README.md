# Architecture Decision Records — HIST (hist-service, local)

Service-level decisions for `hist-service` (module **HIST — Histórico e Auditoria**). These build on the
shared, project-level ADRs and record only what is specific to this service.

## Index (local)

| ADR | Title | Status |
|---|---|---|
| ADR-004 | Immutable append-only audit ledger (write-once) | aceita |
| ADR-005 | Synchronous audit ingestion with producer-side durability (broker-deferred) | aceita |
| ADR-006 | Hash-chained tamper-evident audit records | aceita |

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
- **ADR-004** applies **ADR-001** (hexagonal) and **ADR-002** (independent microservices): hist-service is
  its own hexagonal microservice and the write-once system of record for the audit ledger; producers submit
  facts but do not own the trail.
- **ADR-005** depends on **ADR-004** and honors the project-wide messaging-disabled posture: ingestion is
  synchronous REST with producer-side outbox/retry, idempotent on `factId`, broker deferred.
- **ADR-006** strengthens **ADR-004**: append-only prevents in-place edits at the app layer; hash-chaining
  makes any deeper tampering or gap *detectable* on read. Chain scope is (tenant, subject), inheriting
  **ADR-003** tenant isolation.
- **ADR-003** (multi-tenancy) scopes every record, stream and query per empresa.

## Process
Status vocabulary: `proposta` | `aceita` | `substituída por ADR-NNN`. Numbers are stable and never
renumbered; a revoked decision keeps its number so cross-references survive.
