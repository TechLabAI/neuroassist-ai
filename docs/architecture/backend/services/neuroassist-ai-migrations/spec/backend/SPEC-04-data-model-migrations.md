# SPEC-04 — Data Model & Migrations

> ADR refs: **ADR-002**, **ADR-005**. Contracts: data-model.md (full).

## Objective

Define the two migration locations MIG packages (global, tenant-template),
the tenant registry it reads, and the baseline tenant-template tables it
seeds ahead of PAC/ACS/HIST/EVNT/EDGE going through their own specs.

## Scope

- **In:** `neuroassist_global` schema + `tenant` registry table; `t_<tenantId>`
  tenant-template baseline tables (data-model §3); Flyway history ledger.
- **Out:** business-rule validation of domain data (owned by PAC/ACS/etc.
  once specced); archival/partitioning (out of MVP scope, G3).

## Technical decisions

- Global and tenant-template migrations are **separate Flyway locations**
  with independent version sequences (data-model §5).
- `tenant.schema_name` is a distinct, unique column from `tenant.id` (UUID)
  — the physical schema name MIG derives at provisioning time.
- Enum-like columns (`tipo`, `papel`, `urgencia`, `sensibilidade`, `feedback`,
  `status`) use `CHECK` constraints, not native Postgres enum types, so a
  future owning module can extend allowed values with a plain migration.
- MIG owns the **baseline** of the tenant-template tables until each business
  module's own `/be-architect-specs` run makes that module's data-model the
  binding source (ADR-002 — module contribution without MIG authoring
  business logic going forward).

## Implementation

- `db/migration/global/V0001__init_extensions_and_tenant_registry.sql`
- `db/migration/empresa/V0002__tenant_template_baseline.sql` (tipo_gesto,
  dispositivo_edge, paciente, cuidador, comando, evento)
- `infrastructure`: `FlywayAdapter` configured with two locations, one
  `defaultSchema` per run target (global vs. the specific `t_<tenantId>`).

## Edge cases

- A `tenant` row is inserted (by ACS) without a corresponding schema having
  been provisioned yet → `migrate-all` fan-out will attempt to create/migrate
  it (self-healing on next run) rather than erroring.
- `schema_name` collision on insert → `UNIQUE` constraint rejects at the
  database level; ACS surfaces the conflict to its caller.

## Acceptance criteria (EARS)

`THE SYSTEM SHALL keep global and tenant-template migrations in separate Flyway locations with independent version sequences.`
`THE SYSTEM SHALL enforce schema_name uniqueness on the tenant registry table.`
`THE SYSTEM SHALL express tenant-template enum-like columns as CHECK constraints, not native Postgres enum types.`
`THE SYSTEM SHALL treat a tenant row with no yet-provisioned schema as eligible for provisioning on the next migrate-all fan-out.`
