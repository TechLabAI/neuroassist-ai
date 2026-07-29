<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

> MIG is an infrastructure service. Its "domain" is the **schema lifecycle**, not the business
> domain. Business aggregates (Paciente, Comando, Evento, ...) are defined and migrated *by* MIG
> but *owned* by their respective business services — MIG never encodes their behavior.

## Ubiquitous Language
- **Schema target** — an addressable schema MIG migrates: the global schema
  (`neuroassist_global`) or a tenant schema (`t_<tenantId>`).
- **Global schema** — `neuroassist_global`: shared, cross-tenant objects (tenant registry, and
  any platform-wide reference/config tables).
- **Tenant schema** — `t_<tenantId>`: one isolated schema per empresa (família ou clínica).
- **Tenant template** — the canonical migration set that defines the structure every tenant
  schema must have; the single source of per-tenant DDL.
- **Migration** — a versioned, checksummed unit of schema change (Flyway `V__` / repeatable `R__`).
- **Head** — the latest known version of a migration set.
- **Provisioning** — creating a tenant schema and applying the tenant template to head.
- **Drift** — a divergence between applied history and the current script set (checksum mismatch).

## Aggregates and Entities
- **MigrationTarget** (aggregate root): identity = schema name; holds current version, pending
  migrations, and applied-history reference. Two kinds: `GLOBAL` and `TENANT`.
- **TenantRegistryEntry** (read model, sourced from the global schema): `tenantId`, `tipo`
  (familia | clinica), schema name — the list MIG iterates to migrate all tenants.
- **MigrationHistory** (Flyway-managed, per schema): immutable applied-migration ledger; MIG
  reads it, never hand-edits it.

## Domain Events
> Broker disabled (`messaging.enabled = false`) → these are conceptual lifecycle facts logged to
> the pipeline activity trail (D-423), not published to a bus.
- **SchemaMigrado** ("Schema migrado") — anchor event: a target reached a new head version.
- **EmpresaProvisionada** ("Empresa provisionada") — a new tenant schema was created and migrated
  to head.
- **MigracaoFalhou** ("Migração falhou") — a run aborted (drift or DDL error) on a named schema.

## State Transitions
Per schema target:
`ausente` → (provision) → `criado` → (migrate) → `migrando` → `no-head`
`no-head` → (new version released) → `pendente` → (migrate) → `no-head`
Any state → (checksum drift / DDL error) → `falho` (terminal for the run; requires reviewed fix).

## Invariants
1. A tenant schema is only ever migrated with the canonical tenant template — never ad hoc DDL.
2. Migrations apply strictly in version order; no out-of-order or partial application is recorded
   as success.
3. A schema is never reported "no-head" while pending migrations exist.
4. Applied history is append-only; MIG never mutates or deletes history rows in normal operation.
5. Global-schema migrations that a tenant template depends on must be at head before tenant
   provisioning runs (ordering dependency: global before tenant).
6. Provisioning is idempotent: re-running for an existing tenant never duplicates objects.
