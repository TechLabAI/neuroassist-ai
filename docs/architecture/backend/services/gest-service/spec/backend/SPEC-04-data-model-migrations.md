# SPEC-04 — Data Model and Migrations (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **ADR refs:** ADR-003 (schema-per-tenant), ADR-006
> **DDL canonical source:** `contracts/data-model.md` (executable `sql` blocks)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify the persistence model of `gest-service`, the schema-routing strategy, and the Flyway
migration ownership. The executable DDL in `contracts/data-model.md` is the canonical source;
this spec provides the rationale, constraints summary, and migration plan.

## 2. Scope

**In:**
- Four tables: `tipo_gesto`, `tipo_gesto_transition`, `detection_profile`, `detection_profile_primitive`
- Schema: all live in the `empresa` (per-tenant) schema (no global GEST tables in MVP)
- Flyway migration range: `V0400..V0499` (MIG-owned, centralized migration service)
- Privacy invariant: no biometric-adjacent column in any table

**Out:**
- Application-layer business logic (SPEC-02, SPEC-03)
- DDL for other services/schemas (owned by their respective modules)
- pgVector / embeddings (not used by GEST)

## 3. Schema routing

GEST uses **schema-per-tenant** (`isolationStrategy: schema-per-empresa`) as per ADR-003. Every
business table lives in `t_<tenantId>`. The `neuroassist_global` schema holds no GEST data in MVP.

- THE SYSTEM SHALL route every database operation to the correct tenant schema via
  `SchemaRoutingDataSource` (resolved from JWT `tenant` claim).
- THE SYSTEM SHALL NOT execute cross-schema joins on GEST tables.

## 4. Entity summary

| Table | Schema | Purpose | Key constraints |
|---|---|---|---|
| `tipo_gesto` | empresa | Primitive vocabulary | `ck_tipo_gesto_state`, `ck_tipo_gesto_supported_needs_ref`, `ux_tipo_gesto_nome_active` |
| `tipo_gesto_transition` | empresa | Append-only audit | `ck_transition_to_state`, FK to `tipo_gesto ON DELETE RESTRICT` |
| `detection_profile` | empresa | Immutable versioned bundle | `ux_profile_version`, `ux_profile_single_active` |
| `detection_profile_primitive` | empresa | Snapshot membership | FK to both tables; `ux_profile_primitive` |

## 5. Key invariants enforced at DB level (EARS)

- IF `detectability_state = 'supported'` AND `engine_feature_ref IS NULL`, THEN THE SYSTEM SHALL
  reject the write (`ck_tipo_gesto_supported_needs_ref`) — invariant #5.
- IF a non-deprecated primitive with the same `lower(nome)` already exists in the tenant, THEN
  THE SYSTEM SHALL reject the insert (`ux_tipo_gesto_nome_active`).
- THE SYSTEM SHALL keep at most one `active = true` detection profile per tenant
  (`ux_profile_single_active`).
- THE SYSTEM SHALL enforce `ux_profile_version` uniqueness so the same version number is never
  allocated twice — invariant #3.
- WHEN a `tipo_gesto` row is deleted (restricted by FK), THE SYSTEM SHALL prevent the deletion
  if any `tipo_gesto_transition` or `detection_profile_primitive` references it.

## 6. Privacy invariant

- THE SYSTEM SHALL NOT store any video, image, landmark, confidence value, or biometric datum in
  any GEST table — only abstract primitive identifiers, lifecycle states, opaque engine handles,
  and scalar classification parameters (doc 02 / LGPD compliance).

## 7. Migration ownership

GEST ships **no local Flyway runner**; it is a migration contributor to the centralized MIG service.

| Range | Scope |
|---|---|
| `V0400__gest_initial_schema.sql` | Tables `tipo_gesto`, `tipo_gesto_transition`, `detection_profile`, `detection_profile_primitive` (one migration, all four tables) |
| `V0401..V0499` | Reserved for GEST evolution |
| Global ext block | `pgcrypto` extension (global schema; via MIG global migration set) |

Script extraction: `python scripts/be-migration-generator.py --service gest-service` reads the
`sql` blocks in `contracts/data-model.md` in order and produces the `V0400__gest_initial_schema.sql`
migration artifact.

## 8. Acceptance criteria

- THE SYSTEM SHALL apply the `V0400` migration to a fresh tenant schema without error.
- After migration, all four tables exist with their constraints, indexes, and comments.
- Attempting to insert a `tipo_gesto` row with `state = 'supported'` and `engine_feature_ref = null`
  fails at the DB level (`ck_tipo_gesto_supported_needs_ref`).
- Attempting to insert two active profiles for the same tenant fails at the DB level
  (`ux_profile_single_active`).
- Schema routing: operations from tenant A do not read or mutate tenant B's data.
