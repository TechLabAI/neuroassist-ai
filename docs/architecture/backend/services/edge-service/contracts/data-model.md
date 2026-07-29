# EDGE — Data Model (`edge-service`)

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service` · Package: `com.neuroassist.edge`
> Multi-tenant: **schema-per-empresa** (ADR-003 shared) · DDL owned by centralized **MIG** (Flyway).
> Executable `sql` blocks below are the canonical DDL: `scripts/be-migration-generator.py` extracts them
> **in order** and concatenates into the MIG migration set. EARS notation (D-057) governs constraints.
> Guide: `docs/architecture/backend/standards/ears-notation.md`.

## 1. Conventions

- **PK strategy:** surrogate `uuid` primary keys, `DEFAULT gen_random_uuid()` (pgcrypto).
- **Timestamps:** `timestamptz`, UTC, `DEFAULT now()`; `created_at` immutable, `updated_at` on mutation.
- **Server-clock liveness:** `ultima_sincronizacao` is stamped by the **server** at heartbeat accept
  (never the client clock) — mitigates edge clock skew (doc 06 R5).
- **Derived, not stored:** `status` (`online`/`offline`) is **not** a column. It is computed at read time
  from `now − ultima_sincronizacao` vs. the staleness threshold (ADR-006, doc 05). Persisting it would
  reintroduce the false-"online" risk (doc 06 R2).
- **Soft-retire, never hard-delete:** a replaced device is `retired`, not deleted, so historical
  `Evento`/`Comando` rows that reference `dispositivo_edge_id` keep resolving (doc 05 Data Lifecycle).
- **Defense-in-depth tenancy:** every row carries `tenant_id` mirrored, matching PAC's convention, so a
  schema-routing bug cannot silently cross tenants (doc 05 Schema Strategy).
- **Encoding:** UTF-8. Secrets are stored **hashed only** — no plaintext credential or enrollment token
  is ever persisted (ADR-005).
- **Schema routing:** every business table lives in the tenant schema (`-- schema: empresa`). The
  `neuroassist_global` schema holds **no** EDGE business data in the MVP.
- **Privacy invariant (doc 02 / Framing #6):** no column stores video, image, landmark, or any biometric
  datum — only device metadata, version pointers, and a `paciente_id` reference (personal by association).

Pairing states (ADR-005 / Framing): `registered`, `paired`, `unpaired`, `retired`.

---

## 0. Extensions

```sql
-- schema: global
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

---

## 2. Entities

### 2.1 `dispositivo_edge` [empresa]

The `DispositivoEdge` aggregate root: the registered backend identity of an edge agent, its exclusive
pairing to a patient, and its last-heartbeat liveness inputs. `paciente_id` is a cross-service reference
into PAC (**not** a FK); `null` until paired. `status` is intentionally absent — it is derived at read
time. At most one row per `paciente_id` may be `paired` (exclusive pairing, invariant #1).

```sql
-- schema: empresa
CREATE TABLE dispositivo_edge (
    id                       uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id                varchar(60)   NOT NULL,
    label                    varchar(160)  NULL,
    paciente_id              uuid          NULL,
    pairing_state            varchar(20)   NOT NULL DEFAULT 'registered',
    versao                   varchar(40)   NULL,
    ultima_sincronizacao     timestamptz   NULL,
    running_profile_version  bigint        NULL,
    row_version              bigint        NOT NULL DEFAULT 0,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    updated_at               timestamptz   NOT NULL DEFAULT now(),
    created_by               varchar(120)  NULL,
    updated_by               varchar(120)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_dispositivo_edge_state
        CHECK (pairing_state IN ('registered','paired','unpaired','retired')),
    CONSTRAINT ck_dispositivo_edge_paired_has_patient
        CHECK (pairing_state <> 'paired' OR paciente_id IS NOT NULL)
);

CREATE UNIQUE INDEX ux_dispositivo_edge_active_pairing
    ON dispositivo_edge (paciente_id)
    WHERE pairing_state = 'paired';

CREATE INDEX ix_dispositivo_edge_paciente ON dispositivo_edge (paciente_id);
CREATE INDEX ix_dispositivo_edge_state ON dispositivo_edge (pairing_state);
CREATE INDEX ix_dispositivo_edge_sync ON dispositivo_edge (ultima_sincronizacao);

COMMENT ON TABLE dispositivo_edge IS 'EDGE: edge-device identity, pairing and liveness inputs (tenant-scoped, ADR-005/006).';
COMMENT ON COLUMN dispositivo_edge.paciente_id IS 'Cross-service ref to PAC Paciente; NOT a FK; null until paired.';
COMMENT ON COLUMN dispositivo_edge.ultima_sincronizacao IS 'Server-stamped at heartbeat accept; input to derived liveness.';
COMMENT ON COLUMN dispositivo_edge.running_profile_version IS 'Profile version the device last reported running (drift check).';
```

**Invariants:**
- THE SYSTEM SHALL keep at most one `paired` device per `paciente_id` (`ux_dispositivo_edge_active_pairing`)
  — exclusive pairing, invariant #1 (ADR-005). API `409 pairing-conflict`.
- IF `pairing_state = 'paired'` AND `paciente_id IS NULL`, THEN THE SYSTEM SHALL reject the write
  (`ck_dispositivo_edge_paired_has_patient`).
- THE SYSTEM SHALL never persist a `status` column; liveness is derived (ADR-006).
- THE SYSTEM SHALL never hard-delete a device row; retirement is a transition to `retired`.

### 2.2 `dispositivo_enrollment` [empresa]

The one-time, short-lived **bootstrap-trust** token (doc 06 G4). Only its **hash** is stored. Minted at
registration (api-contract §2.1), consumed exactly once at enrollment (§2.2), then inert.

```sql
-- schema: empresa
CREATE TABLE dispositivo_enrollment (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            varchar(60)   NOT NULL,
    dispositivo_edge_id  uuid          NOT NULL,
    token_hash           varchar(200)  NOT NULL,
    expires_at           timestamptz   NOT NULL,
    consumed_at          timestamptz   NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT ux_dispositivo_enrollment_token UNIQUE (token_hash),
    CONSTRAINT fk_enrollment_device
        FOREIGN KEY (dispositivo_edge_id) REFERENCES dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE INDEX ix_enrollment_device ON dispositivo_enrollment (dispositivo_edge_id);

COMMENT ON TABLE dispositivo_enrollment IS 'EDGE: single-use, TTL-bounded device enrollment (claim) token — hash only (doc 06 G4).';
```

**Invariants:**
- THE SYSTEM SHALL store only the enrollment token hash, never the plaintext token.
- IF an enrollment token is expired (`now > expires_at`) or already consumed (`consumed_at IS NOT NULL`),
  THEN THE SYSTEM SHALL reject enrollment (API `401 enrollment-invalid`).
- WHEN enrollment succeeds, THE SYSTEM SHALL set `consumed_at = now()` atomically with credential issuance.

### 2.3 `dispositivo_credential` [empresa]

Device-authentication material (ADR-005). An OAuth2 `client_credentials` client per device: a `client_id`
plus the **hash** of its secret. Issued at enrollment, rotated on re-pairing, revoked on retire. Never a
plaintext secret (doc 05).

```sql
-- schema: empresa
CREATE TABLE dispositivo_credential (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            varchar(60)   NOT NULL,
    dispositivo_edge_id  uuid          NOT NULL,
    client_id            varchar(120)  NOT NULL,
    secret_hash          varchar(200)  NOT NULL,
    status               varchar(20)   NOT NULL DEFAULT 'active',
    issued_at            timestamptz   NOT NULL DEFAULT now(),
    revoked_at           timestamptz   NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_dispositivo_credential_client UNIQUE (client_id),
    CONSTRAINT ck_dispositivo_credential_status CHECK (status IN ('active','revoked')),
    CONSTRAINT fk_credential_device
        FOREIGN KEY (dispositivo_edge_id) REFERENCES dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_dispositivo_credential_active
    ON dispositivo_credential (dispositivo_edge_id)
    WHERE status = 'active';

CREATE INDEX ix_credential_device ON dispositivo_credential (dispositivo_edge_id);

COMMENT ON TABLE dispositivo_credential IS 'EDGE: per-device OAuth2 client_credentials material — hash only, one active per device (ADR-005).';
```

**Invariants:**
- THE SYSTEM SHALL keep at most one `active` credential per device (`ux_dispositivo_credential_active`).
- THE SYSTEM SHALL store only `secret_hash`, never the plaintext secret; the secret is returned to the
  agent exactly once at enrollment (api-contract §2.2).
- WHEN a device is retired or re-paired, THE SYSTEM SHALL set the superseded credential `status='revoked'`
  and stamp `revoked_at` so a stale secret can never authenticate (doc 06 R3).

### 2.4 `dispositivo_edge_transition` [empresa]

Append-only audit of **pairing-lifecycle** and **liveness** transitions (ADR-005 invariant #3). Safety
evidence — retained for the full pilot (doc 05). Liveness rows capture only *changes* (`online↔offline`),
never every heartbeat, to avoid unbounded growth (doc 05 Data Lifecycle). No `UPDATE`/`DELETE`.

```sql
-- schema: empresa
CREATE TABLE dispositivo_edge_transition (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            varchar(60)   NOT NULL,
    dispositivo_edge_id  uuid          NOT NULL,
    kind                 varchar(20)   NOT NULL,
    from_state           varchar(20)   NULL,
    to_state             varchar(20)   NOT NULL,
    actor                varchar(120)  NOT NULL,
    reason               varchar(500)  NULL,
    at                   timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT ck_transition_kind CHECK (kind IN ('pairing','liveness')),
    CONSTRAINT fk_transition_device
        FOREIGN KEY (dispositivo_edge_id) REFERENCES dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE INDEX ix_transition_device ON dispositivo_edge_transition (dispositivo_edge_id, at);

COMMENT ON TABLE dispositivo_edge_transition IS 'EDGE: append-only pairing + liveness transition audit (ADR-005 invariant #3).';
COMMENT ON COLUMN dispositivo_edge_transition.actor IS 'Operator principal for pairing; ''system'' for derived liveness transitions.';
```

**Invariants:**
- WHEN a pairing transition succeeds, THE SYSTEM SHALL append exactly one `kind='pairing'` row capturing
  `from_state`, `to_state`, `actor`, `reason`, `at`.
- WHEN a liveness read observes a crossing (`online↔offline`) or a heartbeat resumes an offline device,
  THE SYSTEM SHALL append exactly one `kind='liveness'` row with `actor='system'` — no silent change.
- THE SYSTEM SHALL treat this table as append-only (no update/delete).

---

## 3. Relations (ASCII)

```
dispositivo_edge 1 ──< dispositivo_enrollment          (one-time claim tokens)
dispositivo_edge 1 ──< dispositivo_credential          (device auth material; 1 active)
dispositivo_edge 1 ──< dispositivo_edge_transition     (pairing + liveness audit)
dispositivo_edge . ─ ─ paciente_id ─ ─> PAC.Paciente   (cross-service ref, NOT a FK)
```

Not stored here (owned by peers, referenced by id only across REST): `Paciente` (PAC), the detection
profile / version (GEST), `Evento` (EVNT), caregiver identity (ACS). EDGE stores only ids and version
pointers across those boundaries.

---

## 4. Schema-level mapping

| Table | Schema |
|---|---|
| `dispositivo_edge` | `empresa` (per-tenant `t_<tenantId>`) |
| `dispositivo_enrollment` | `empresa` |
| `dispositivo_credential` | `empresa` |
| `dispositivo_edge_transition` | `empresa` |
| pgcrypto extension | `global` (once) |

---

## 5. Migration plan (MIG-owned)

EDGE ships **no** local Flyway; it contributes scripts to the centralized MIG set. Reserved range for the
EDGE initial schema (per tenant schema): `V0700__edge_initial_schema.sql` … `V0799` reserved for EDGE
evolution (non-colliding with PAC `V0001..`, GEST `V0400..`). The extension block goes to the global set.

- All four tables are created in the same initial migration, extensions first, in the block order above.
- Future evolution (e.g. mTLS credential columns — SPEC-06 future hardening) uses new `V07xx` scripts —
  never edits an applied migration.
