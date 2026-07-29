<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
Schema-per-empresa (ADR-003 shared). The TipoGesto vocabulary and detection profiles are
**tenant-scoped** — each unidade de cuidado (família/clínica) governs its own primitive catalog,
detectability states, and profile versions inside its `t_<tenantId>` schema. The `neuroassist_global`
schema holds no GEST business data in the MVP (no cross-tenant primitive registry); global promotion
of a shared vocabulary is a deliberate non-goal until multi-clinic expansion is validated.

## Initial Entities
> Table shapes are provisional; the authoritative data model is produced in `/be-architect-specs`.

- **`tipo_gesto`** — `id` (PK), `nome`, `detectability_state`
  (`registered|pending_training|supported|deprecated`), `engine_feature_ref` (nullable until
  `supported`), `created_at`, `updated_at`, audit columns. Unique `nome` per tenant.
- **`detection_profile`** — `id` (PK), `version` (monotonic per tenant, unique), `active` (bool),
  `classification_params` (jsonb: confidence threshold, min/max window ms), `published_at`.
  Immutable once published.
- **`detection_profile_primitive`** — join of `detection_profile` × `tipo_gesto` (only `supported`
  primitives at publish time), snapshotting the primitive's parameters into the profile version.
- **`tipo_gesto_transition`** — append-only audit of detectability transitions: `tipo_gesto_id`,
  `from_state`, `to_state`, `actor`, `reason`, `at`. Backs invariant #4 (no silent regression).

> Not stored here: `Comando` (PAC), `Evento` (EVNT), `DispositivoEdge` (EDGE), `Paciente`/`Cuidador`
> (PAC/ACS). `detection_profile` may carry a resolved device reference by id only.

## Migration Strategy
All DDL is owned by the **centralized MIG service** (Flyway across all schemas). gest-service ships
**no** local Flyway migrations — it contributes migration scripts to MIG's managed set. Every tenant
schema receives the same GEST tables; the global schema receives none for GEST in the MVP.

## Data Lifecycle
- **TipoGesto:** long-lived; never hard-deleted while referenced by any historical `Comando`/`Evento`
  — `deprecated` instead (referential-integrity + audit for a safety product).
- **DetectionProfile:** immutable and versioned; retained for the full pilot to reconstruct exactly
  which profile any device ran at any time. Old versions are archived, not deleted.
- **Transition audit (`tipo_gesto_transition`):** append-only, retained indefinitely in the MVP —
  it is safety evidence, not operational churn.
- **LGPD:** GEST holds no personal or biometric data — only an abstract primitive vocabulary and
  detection parameters. No video/image/landmark data is ever persisted (privacy invariant, doc 02).
  This keeps GEST outside the LGPD sensitive-data surface even though the project has `lgpd = true`.
