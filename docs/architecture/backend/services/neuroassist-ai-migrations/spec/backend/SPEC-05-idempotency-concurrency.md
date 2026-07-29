# SPEC-05 — Idempotency & Concurrency

> ADR refs: **ADR-002**, **ADR-005**. Contracts: api-contract §2/§3,
> data-model §4.

## Objective

Guarantee that a migration re-run has no additional effect once a schema is
at head, that two concurrent runs cannot race the same schema, and that a
checksum-drift attempt aborts instead of corrupting a schema (doc 01
Architectural Principles §2/§3, R4/R5).

## Scope

- **In:** Flyway-history-driven re-run idempotency, advisory locking per
  schema, checksum-drift fail-fast.
- **Out:** rollback (not offered — forward-only, no down-migrations in MVP),
  tenant deprovisioning (G1).

## Technical decisions

- **Re-run idempotency** is structural: `flyway_schema_history` records
  every applied version; Flyway skips already-applied versions, so a second
  run applies nothing new and exits 0.
- **Concurrency:** a PostgreSQL advisory lock per target schema
  (`pg_advisory_lock(hashtext(schemaName))`) prevents two MIG runs (or a run
  + a provisioning call) racing the same schema.
- **Checksum drift:** `validateOnMigrate=true`; a mismatch between a
  migration file and its recorded checksum aborts that schema's apply with
  no auto-repair (R4).
- **Partial failure:** per-schema isolation — a failed schema stops at its
  last good version and is resumable on the next run; other schemas are
  unaffected (R2/R5).

## Implementation

- `infrastructure`: `MigrationLock` wraps each schema apply in
  advisory lock/unlock.
- `FlywayAdapter` sets `validateOnMigrate=true`, `outOfOrder=false`.

## Edge cases

- Crash mid-apply leaves a `success=false` history row → blocks re-apply
  until an operator repairs it (Flyway `repair`), then resumes.
- Re-provision of an already-current tenant → idempotent no-op (SPEC-03).
- Two `migrate-all` invocations overlap → the second waits on the advisory
  lock for each schema in turn; no double-apply.

## Acceptance criteria (EARS)

`WHEN a schema is already at head and migrate runs again, THE SYSTEM SHALL apply nothing and exit 0.`
`WHILE a migration run holds a schema's advisory lock, THE SYSTEM SHALL prevent a second concurrent run from applying to that same schema.`
`IF a run fails partway, THEN THE SYSTEM SHALL leave the schema at its last successful version and SHALL resume from the first pending version on the next run.`
`IF a migration file's checksum does not match its recorded history, THEN THE SYSTEM SHALL abort that schema's apply without auto-repair.`
