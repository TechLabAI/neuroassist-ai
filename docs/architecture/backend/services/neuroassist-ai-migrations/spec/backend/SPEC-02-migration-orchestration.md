# SPEC-02 — Migration Orchestration (core)

> ADR refs: **ADR-002** (centralized Flyway, all schemas). Contracts:
> api-contract §2/§3, data-model §5.

## Objective

Define how MIG orchestrates a `migrate-all` / `migrate-global` run: ordering
(global before tenant), fan-out over every registered tenant schema, and the
run report that gates the deploy pipeline.

## Scope

- **In:** run modes, global→tenant ordering, per-schema apply, run report,
  exit codes.
- **Out:** tenant provisioning of a *new* schema (SPEC-03), idempotency/
  concurrency mechanics (SPEC-05).

## Technical decisions

- **Single owner of DDL** (ADR-002): only MIG runs Flyway; business services
  never self-migrate.
- **Ordering invariant:** global migrations reach head before any tenant
  schema is migrated in the same run (doc 05 §Migration Strategy, A5).
- **Fan-out:** `migrate-all` reads the `tenant` registry (data-model §2.2)
  and applies the tenant-template set to each existing `t_<tenantId>`
  schema, one at a time (MVP: no parallelism).
- **Fail-fast, per-schema isolation:** a failure on one tenant schema does
  not abort schemas already succeeded or skip remaining ones; it is recorded
  in the run report and the exit code reflects the worst outcome.

## Implementation

- `application`: `MigrateAllSchemas` use case — resolves the tenant list,
  builds an ordered `MigrationPlan` (global first, then tenants), delegates
  each schema apply to the `FlywayAdapter` (infrastructure), aggregates the
  `RunReport`.
- `domain`: `SchemaTarget` (global|tenant), `MigrationPlan`, `RunReport` —
  pure, no Spring/JDBC.

## Edge cases

- Empty tenant registry → `migrate-all` migrates the global schema only and
  exits 0.
- A tenant schema mid-fan-out fails → its entry is `failed` in the report,
  remaining tenants are still attempted, exit code is 2.
- `migrate-global` invoked while tenant schemas are behind → global proceeds;
  tenants remain pending (not this run's concern).

## Acceptance criteria (EARS)

`THE SYSTEM SHALL migrate the global schema to head before applying any tenant-template migration in the same run.`
`WHEN migrate-all is invoked, THE SYSTEM SHALL apply pending migrations to neuroassist_global and to every schema listed in the tenant registry.`
`IF one tenant schema fails during fan-out, THEN THE SYSTEM SHALL continue attempting the remaining tenant schemas and SHALL report the failed schema by name.`
`THE SYSTEM SHALL emit one run report per invocation containing a per-schema applied/pending/failed entry, with report.exitCode equal to the process exit code.`
