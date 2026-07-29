# GEST — Data Model (`gest-service`)

> Module: **GEST — Detecção de Gesto (Edge)** · Service: `gest-service` · Package: `com.neuroassist.gest`
> Multi-tenant: **schema-per-empresa** (ADR-003 shared) · DDL owned by centralized **MIG** (Flyway).
> Executable `sql` blocks below are the canonical DDL: `scripts/be-migration-generator.py` extracts
> them **in order** and concatenates into the MIG migration set. EARS notation (D-057) governs
> constraints. Guide: `docs/architecture/backend/standards/ears-notation.md`.

## 1. Conventions

- **PK strategy:** surrogate `uuid` primary keys, `DEFAULT gen_random_uuid()` (pgcrypto).
- **Timestamps:** `timestamptz`, UTC, `DEFAULT now()`; `created_at` immutable, `updated_at` on mutation.
- **Soft-delete:** GEST does **not** hard-delete referenced primitives; retirement is the
  `deprecated` detectability state (doc 05 "Data Lifecycle"). No `deleted_at` column.
- **Encoding:** UTF-8; `nome` stored trimmed; uniqueness compared case-insensitively via `lower(nome)`.
- **Schema routing:** every business table lives in the tenant schema (`-- schema: empresa`). The
  `neuroassist_global` schema holds **no** GEST business data in the MVP (doc 05 §Schema Strategy).
- **Privacy invariant (doc 02 / doc 05 §LGPD):** no column stores video, image, landmark, or any
  biometric datum — only abstract primitive identifiers, states, opaque engine handles, and numeric
  classification parameters. GEST is outside the LGPD sensitive-data surface.

Detectability states (ADR-006): `registered`, `pending_training`, `supported`, `deprecated`.

---

## 0. Extensions

```sql
-- schema: global
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

---

## 2. Entities

### 2.1 `tipo_gesto` [empresa]

The authoritative primitive (facial-movement) vocabulary. Aggregate root. `engine_feature_ref` is the
opaque handle binding a primitive to a real edge-engine feature; it MUST be non-null before the
primitive can reach `supported` (invariant #5, ADR-006). `nome` is unique per tenant among
non-deprecated rows so a deprecated name can be re-registered later.

```sql
-- schema: empresa
CREATE TABLE tipo_gesto (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    nome                 varchar(120)  NOT NULL,
    detectability_state  varchar(20)   NOT NULL DEFAULT 'registered',
    engine_feature_ref   varchar(200)  NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           varchar(120)  NULL,
    updated_by           varchar(120)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_tipo_gesto_state
        CHECK (detectability_state IN ('registered','pending_training','supported','deprecated')),
    CONSTRAINT ck_tipo_gesto_supported_needs_ref
        CHECK (detectability_state <> 'supported' OR engine_feature_ref IS NOT NULL)
);

CREATE UNIQUE INDEX ux_tipo_gesto_nome_active
    ON tipo_gesto (lower(nome))
    WHERE detectability_state <> 'deprecated';

CREATE INDEX ix_tipo_gesto_state ON tipo_gesto (detectability_state);

COMMENT ON TABLE tipo_gesto IS 'GEST: extensible facial-movement primitive catalog (tenant-scoped, ADR-006).';
COMMENT ON COLUMN tipo_gesto.engine_feature_ref IS 'Opaque edge-engine feature handle; required before supported.';
```

**Invariants:**
- IF `detectability_state = 'supported'` AND `engine_feature_ref IS NULL`, THEN THE SYSTEM SHALL reject
  the write (`ck_tipo_gesto_supported_needs_ref`) — invariant #5.
- IF a non-deprecated primitive with the same `lower(nome)` exists in the tenant, THEN THE SYSTEM SHALL
  reject the insert (`ux_tipo_gesto_nome_active`) — API `409 tipo-gesto-duplicate`.
- THE SYSTEM SHALL never hard-delete a `tipo_gesto` row; retirement is a transition to `deprecated`.

### 2.2 `tipo_gesto_transition` [empresa]

Append-only audit of every detectability transition. Backs invariant #4 (no silent regression) and is
safety evidence retained indefinitely (doc 05). No `UPDATE`/`DELETE` in application code.

```sql
-- schema: empresa
CREATE TABLE tipo_gesto_transition (
    id             uuid          NOT NULL DEFAULT gen_random_uuid(),
    tipo_gesto_id  uuid          NOT NULL,
    from_state     varchar(20)   NULL,
    to_state       varchar(20)   NOT NULL,
    actor          varchar(120)  NOT NULL,
    reason         varchar(500)  NULL,
    at             timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT ck_transition_to_state
        CHECK (to_state IN ('registered','pending_training','supported','deprecated')),
    CONSTRAINT fk_transition_tipo_gesto
        FOREIGN KEY (tipo_gesto_id) REFERENCES tipo_gesto (id) ON DELETE RESTRICT
);

CREATE INDEX ix_transition_tipo_gesto ON tipo_gesto_transition (tipo_gesto_id, at);

COMMENT ON TABLE tipo_gesto_transition IS 'GEST: append-only detectability transition audit (invariant #4).';
```

**Invariants:**
- WHEN a `tipo_gesto` detectability transition succeeds, THE SYSTEM SHALL append exactly one row here
  capturing `from_state`, `to_state`, `actor`, `reason`, `at`.
- IF `to_state` regresses from `supported` to a lower state, THEN THE SYSTEM SHALL require a non-null
  `reason` — API `400 regression-reason-required`.
- THE SYSTEM SHALL treat this table as append-only (no update/delete).

### 2.3 `detection_profile` [empresa]

An immutable, versioned bundle configuring the edge engine. `version` is strictly monotonic per tenant
and never reused (invariant #3). At most one row per tenant is `active`. `contract_version` records the
detection-contract semantic version the profile was published under.

```sql
-- schema: empresa
CREATE TABLE detection_profile (
    id                    uuid          NOT NULL DEFAULT gen_random_uuid(),
    version               bigint        NOT NULL,
    active                boolean       NOT NULL DEFAULT false,
    contract_version      varchar(20)   NOT NULL,
    classification_params jsonb         NOT NULL,
    published_at          timestamptz   NOT NULL DEFAULT now(),
    published_by          varchar(120)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_profile_version UNIQUE (version),
    CONSTRAINT ck_profile_params_object CHECK (jsonb_typeof(classification_params) = 'object')
);

CREATE UNIQUE INDEX ux_profile_single_active ON detection_profile (active) WHERE active = true;
CREATE INDEX ix_profile_active ON detection_profile (active);

COMMENT ON TABLE detection_profile IS 'GEST: immutable versioned detection profile pulled by the edge (invariants #2,#3).';
COMMENT ON COLUMN detection_profile.classification_params IS 'jsonb: confidenceThreshold, minWindowMs, maxWindowMs, compositeMaxGapMs.';
```

**Invariants:**
- THE SYSTEM SHALL assign `version` strictly monotonic per tenant and SHALL never reuse a value
  (`ux_profile_version`) — invariant #3.
- THE SYSTEM SHALL keep at most one `active = true` profile per tenant (`ux_profile_single_active`).
- THE SYSTEM SHALL treat a published profile as immutable (no `UPDATE` of content columns; only the
  `active` flag flips to false when superseded).

### 2.4 `detection_profile_primitive` [empresa]

Snapshot join of a profile version × the `supported` primitives it embedded, freezing each primitive's
`engine_feature_ref` at publish time so the edge configures deterministically even if the catalog later
changes (invariant #2).

```sql
-- schema: empresa
CREATE TABLE detection_profile_primitive (
    id                    uuid          NOT NULL DEFAULT gen_random_uuid(),
    detection_profile_id  uuid          NOT NULL,
    tipo_gesto_id         uuid          NOT NULL,
    nome_snapshot         varchar(120)  NOT NULL,
    engine_feature_ref    varchar(200)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_profile_primitive_profile
        FOREIGN KEY (detection_profile_id) REFERENCES detection_profile (id) ON DELETE CASCADE,
    CONSTRAINT fk_profile_primitive_tipo
        FOREIGN KEY (tipo_gesto_id) REFERENCES tipo_gesto (id) ON DELETE RESTRICT,
    CONSTRAINT ux_profile_primitive UNIQUE (detection_profile_id, tipo_gesto_id)
);

CREATE INDEX ix_profile_primitive_profile ON detection_profile_primitive (detection_profile_id);

COMMENT ON TABLE detection_profile_primitive IS 'GEST: snapshot of supported primitives embedded in a profile version.';
```

**Invariants:**
- THE SYSTEM SHALL include a primitive here only if it was `supported` at the profile's publish time
  (`engine_feature_ref` therefore non-null) — invariant #2.
- WHEN a profile is deleted (never in MVP; archival only), THE SYSTEM SHALL cascade its snapshot rows
  (`ON DELETE CASCADE`) while preserving the referenced `tipo_gesto` (`ON DELETE RESTRICT`).

---

## 3. Relations (ASCII)

```
tipo_gesto 1 ──< tipo_gesto_transition        (audit trail per primitive)
tipo_gesto 1 ──< detection_profile_primitive  (snapshot membership)
detection_profile 1 ──< detection_profile_primitive
```

Not stored here (owned by peers, referenced by id only across REST): `Comando` (PAC), `Evento` (EVNT),
`DispositivoEdge` (EDGE), `Paciente`/`Cuidador` (PAC/ACS). Device resolution for the edge pull is done
via EDGE at request time — no device FK is persisted in GEST.

---

## 4. Schema-level mapping

| Table | Schema |
|---|---|
| `tipo_gesto` | `empresa` (per-tenant `t_<tenantId>`) |
| `tipo_gesto_transition` | `empresa` |
| `detection_profile` | `empresa` |
| `detection_profile_primitive` | `empresa` |
| pgcrypto extension | `global` (once) |

---

## 5. Migration plan (MIG-owned)

GEST ships **no** local Flyway; it contributes scripts to the centralized MIG set. Reserved range for
the GEST initial schema (per tenant schema): `V0400__gest_initial_schema.sql` … `V0499` reserved for
GEST evolution. The extension block goes to the global migration set.

- All four tables are created in the same initial migration, extensions first, in the block order above.
- Future evolution (new classification params, additional states) uses new `V04xx` scripts — never
  edits an applied migration.
