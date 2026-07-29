# SPEC-04 — Data Model and Migrations (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge
> **ADR refs:** ADR-003 (schema-per-tenant), ADR-005, ADR-006
> **DDL canonical source:** `contracts/data-model.md` (executable `sql` blocks)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify the persistence model of `edge-service`, the schema-routing strategy, and the Flyway migration
ownership. The executable DDL in `contracts/data-model.md` is the canonical source; this spec provides
rationale, constraint summary, and the migration plan.

## 2. Scope

**In:**
- Four tables: `dispositivo_edge`, `dispositivo_enrollment`, `dispositivo_credential`,
  `dispositivo_edge_transition`
- Schema: all live in the `empresa` (per-tenant) schema (no global EDGE tables in MVP)
- Flyway migration range: `V0700..V0799` (MIG-owned)
- Privacy invariant: no biometric column; secrets stored hashed only

**Out:**
- Application-layer logic (SPEC-02, SPEC-03, SPEC-11)
- DDL for other services/schemas (owned by their modules)

## 3. Schema routing

EDGE uses **schema-per-tenant** (`schema-per-empresa`, ADR-003). Every business table lives in
`t_<tenantId>`; `tenant_id` is mirrored on each row for defense-in-depth (doc 05). `neuroassist_global`
holds no EDGE data in MVP.

- THE SYSTEM SHALL route every database operation to the correct tenant schema via
  `SchemaRoutingDataSource` (resolved from the token `tenant` claim).
- THE SYSTEM SHALL NOT execute cross-schema joins on EDGE tables.

## 4. Entity summary

| Table | Schema | Purpose | Key constraints |
|---|---|---|---|
| `dispositivo_edge` | empresa | Device identity, pairing, liveness inputs | `ck_dispositivo_edge_state`, `ck_dispositivo_edge_paired_has_patient`, `ux_dispositivo_edge_active_pairing` |
| `dispositivo_enrollment` | empresa | One-time claim token (hash) | `ux_dispositivo_enrollment_token`, FK `ON DELETE RESTRICT` |
| `dispositivo_credential` | empresa | Per-device auth material (hash) | `ux_dispositivo_credential_client`, `ux_dispositivo_credential_active`, `ck_..._status` |
| `dispositivo_edge_transition` | empresa | Append-only pairing + liveness audit | `ck_transition_kind`, FK `ON DELETE RESTRICT` |

## 5. Key invariants enforced at DB level (EARS)

- THE SYSTEM SHALL keep at most one `paired` device per `paciente_id`
  (`ux_dispositivo_edge_active_pairing`) — invariant #1.
- IF `pairing_state = 'paired'` AND `paciente_id IS NULL`, THEN THE SYSTEM SHALL reject the write
  (`ck_dispositivo_edge_paired_has_patient`).
- THE SYSTEM SHALL keep at most one `active` credential per device (`ux_dispositivo_credential_active`).
- THE SYSTEM SHALL enforce `ux_dispositivo_enrollment_token` and `ux_dispositivo_credential_client`
  uniqueness.
- WHEN a `dispositivo_edge` row is referenced by an enrollment/credential/transition, THE SYSTEM SHALL
  prevent its deletion (`ON DELETE RESTRICT`) — devices are retired, never deleted.

## 6. Privacy invariant

- THE SYSTEM SHALL NOT store any video, image, landmark, or biometric datum in any EDGE table — only
  device metadata, version pointers, a `paciente_id` reference, and **hashed** secrets (doc 02 / LGPD).
- THE SYSTEM SHALL never store a plaintext enrollment token or credential secret.

## 7. Migration ownership

EDGE ships **no local Flyway runner**; it is a migration contributor to the centralized MIG service.

| Range | Scope |
|---|---|
| `V0700__edge_initial_schema.sql` | Four tables (one migration, all four) — non-colliding with PAC `V0001..` and GEST `V0400..` |
| `V0701..V0799` | Reserved for EDGE evolution (e.g. mTLS credential columns — SPEC-06 future hardening) |
| Global ext block | `pgcrypto` (global schema; via MIG global migration set) |

Script extraction: `python scripts/be-migration-generator.py --service edge-service` reads the `sql`
blocks in `contracts/data-model.md` in order and produces the `V0700__edge_initial_schema.sql` artifact.

## 8. Acceptance criteria

- THE SYSTEM SHALL apply the `V0700` migration to a fresh tenant schema without error; all four tables
  exist with their constraints, indexes, and comments.
- Inserting two `paired` devices for the same patient fails at the DB level
  (`ux_dispositivo_edge_active_pairing`).
- Inserting a `paired` device with `paciente_id = null` fails (`ck_dispositivo_edge_paired_has_patient`).
- Two `active` credentials for one device fail (`ux_dispositivo_credential_active`).
- Schema routing: operations from tenant A do not read or mutate tenant B's data.
