<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
Two classes of schema, both owned by MIG:
- **Global schema — `neuroassist_global`:** cross-tenant objects. Minimally the **tenant
  registry** (list of empresas + their schema names + `tipo` familia|clinica) plus any
  platform-wide reference/config data. This registry is the list MIG iterates to migrate all
  tenants.
- **Tenant schema — `t_<tenantId>`:** one per empresa, produced from the canonical tenant
  template. Holds the business tables owned by PAC/EVNT/ACS/HIST/EDGE/etc.: Paciente, Cuidador,
  TipoGesto, Comando, Evento, DispositivoEdge (BA draft data model, 7 entities incl. Tenant).
Isolation is `schema-per-tenant`; there is no row-level tenant column strategy.

## Initial Entities
> MIG does not *own* these business entities — it owns the **DDL that creates them**. Listed as
> the migration surface, not as MIG aggregates.
- **Global schema:** `tenant` (registry: id, tipo, schema name), plus platform reference tables.
- **Tenant template (per `t_<tenantId>`):** `paciente`, `cuidador`, `tipo_gesto`, `comando`
  (with enums urgencia, sensibilidade; primitive refs to tipo_gesto), `evento` (enum feedback),
  `dispositivo_edge` (enum status). Enum-backed columns rendered per the BA data model.
- MIG's **own** persisted state is only the Flyway history table per schema
  (`flyway_schema_history`).

## Migration Strategy
- **Centralized Flyway**, all schemas, single owner (product decision; ADR-002 local).
- **Global set** and **tenant template set** are separate migration locations; the tenant set is
  parameterized by target schema so the identical template applies to every empresa schema.
- **Ordering:** global migrations reach head before any tenant provisioning that depends on
  them (invariant §5 of framing).
- **Baseline:** the P2 docs-skeleton already establishes global (`neuroassist_global`) and
  multi-tenant scaffolding; MIG formalizes the versioned baseline and the tenant template.
- **Validation:** `validate` on every run; checksum drift aborts (fail-fast, no auto-repair in
  the pipeline). Repeatable migrations (`R__`) for views/functions where appropriate.
- **Provisioning:** create `t_<tenantId>` if absent + migrate to head; idempotent.

## Data Lifecycle
- **Retention & erasure (LGPD):** expressed as reviewed migrations (schema-level structures for
  retention windows / soft-delete / anonymization), keeping the data-lifecycle policy versioned
  and auditable. Actual purge/anonymization *execution* on domain rows belongs to the owning
  business services (e.g. HIST); MIG provides the structural DDL, not the runtime job.
- **Auditability:** the per-schema Flyway history is the immutable ledger of every schema change
  — the primary artifact for a security/LGPD audit of structural changes.
- **Archival:** out of MVP scope; no automated archival/partitioning tooling in the first cut
  (recorded as a gap in doc 06).
- **Tenant offboarding:** deprovisioning (dropping a tenant schema) is **not** automated in MVP;
  it must be an explicit, reviewed operation (destructive DDL guardrail).
