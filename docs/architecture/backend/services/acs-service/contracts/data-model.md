# ACS — Data Model (`acs-service`)

> Module: **ACS — Cuidadores e Acesso** · Service: `acs-service` · Package: `com.neuroassist.acs`
> Multi-tenant: **schema-per-empresa** (ADR-003 shared) · DDL owned by centralized **MIG** (Flyway).
> ACS **straddles both schema tiers**: the `Tenant` directory and JWT `signing_key` material are
> **global** (`neuroassist_global`); `cuidador` and `caregiver_patient_link` are **tenant** data.
> Executable `sql` blocks below are the canonical DDL: `scripts/be-migration-generator.py` extracts
> them **in order** and routes each block by its `-- schema:` header. EARS notation (D-057) governs
> constraints. Guide: `docs/architecture/backend/standards/ears-notation.md`.

## 1. Conventions

- **PK strategy:** surrogate `uuid` primary keys, `DEFAULT uuid_generate_v7()` (PS.001 — time-ordered).
- **Timestamps:** `timestamptz`, UTC, `DEFAULT now()`; `criado_em` immutable, `atualizado_em` on mutation.
- **Audit columns (PS.002):** every aggregate root carries `criado_em`, `criado_por_usuario_id`,
  `atualizado_em`, `atualizado_por_usuario_id`.
- **Soft-delete:** ACS does **not** hard-delete identity/access rows. Deactivation/revocation are
  `status` transitions (`inactive` / `revoked`) — invariant #7. No physical `DELETE` in application code.
- **Encoding:** UTF-8; `login`/`nome` stored trimmed; `login` uniqueness compared case-insensitively via
  `lower(login)`.
- **Cross-schema references (PS.003):** `cuidador.tenant_id` and `caregiver_patient_link.tenant_id` are
  **logical FKs** to the global `tenant.id` (no physical cross-schema FK — schemas must stay independently
  movable). `caregiver_patient_link.paciente_id` is a **logical FK** to a PAC-owned `Paciente`, validated
  at link time via `PacPatientResolvePort`, never a DB constraint.
- **Privacy invariant (doc 01 §Principle 6 / doc 05 §Privacy — LGPD hard rule):** no column stores any
  biometric, image, landmark, or clinical datum. Credentials are stored **only** as a hash; the raw
  credential never reaches persistence.

Enums: `tenant.tipo ∈ {familia, clinica}`; `tenant.status ∈ {provisioning, active, suspended}`;
`cuidador.papel ∈ {cuidadora, coordenador_clinica}`; `cuidador.status ∈ {active, inactive}`;
`caregiver_patient_link.status ∈ {active, revoked}`.

> **Reconciliation note (ADR-004).** ACS's global `tenant` table is the **authoritative** empresa
> directory and supersedes the ad-hoc `empresa` catalog sketched in the generic
> `shared/modelo-dados/global-schema.md`. ACS is the single writer of the tenant directory; MIG seeds
> only via ACS's provisioning request.

---

## 0. Extensions

```sql
-- schema: global
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

---

## 2. Entities

### 2.1 `tenant` [global]

The unit of care and isolation — a `familia` or a `clinica`. Root of the tenant tree; scopes every
downstream aggregate. Lives in `neuroassist_global` because it exists *before* any tenant schema and
describes the tenants themselves. `schema_name` records the `t_<tenantId>` schema MIG materialized;
`status` drives the provisioning saga (ADR-004): a tenant is `active` only after MIG confirms the schema.

```sql
-- schema: global
CREATE TABLE tenant (
    id                          uuid          NOT NULL DEFAULT uuid_generate_v7(),
    nome                        varchar(150)  NOT NULL,
    tipo                        varchar(20)   NOT NULL,
    status                      varchar(20)   NOT NULL DEFAULT 'provisioning',
    schema_name                 varchar(63)   NULL,
    criado_em                   timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id       uuid          NULL,
    atualizado_em               timestamptz   NOT NULL DEFAULT now(),
    atualizado_por_usuario_id   uuid          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_tenant_tipo   CHECK (tipo IN ('familia','clinica')),
    CONSTRAINT ck_tenant_status CHECK (status IN ('provisioning','active','suspended')),
    CONSTRAINT ck_tenant_active_needs_schema
        CHECK (status <> 'active' OR schema_name IS NOT NULL)
);

CREATE UNIQUE INDEX ux_tenant_schema_name ON tenant (schema_name) WHERE schema_name IS NOT NULL;
CREATE INDEX ix_tenant_status ON tenant (status);

COMMENT ON TABLE tenant IS 'ACS: authoritative tenant (empresa) directory; root of the tenant tree (ADR-004).';
COMMENT ON COLUMN tenant.schema_name IS 'Per-tenant schema t_<tenantId> materialized by MIG; required before active.';
```

**Invariants:**
- IF `status = 'active'` AND `schema_name IS NULL`, THEN THE SYSTEM SHALL reject the write
  (`ck_tenant_active_needs_schema`) — no half-open tenant (ADR-004, invariant #8).
- IF `tipo` is not `familia | clinica`, THEN THE SYSTEM SHALL reject the write (`ck_tenant_tipo`).
- THE SYSTEM SHALL never hard-delete a tenant; off-boarding transitions `status` to `suspended`.

### 2.2 `signing_key` [global]

The JWT signing-key material (ADR-005). Global because keys are platform-wide, not tenant-scoped, and
because the JWK set aggregates across all keys. Private key material is stored encrypted at rest (or held
in an external secret manager in a later iteration — doc 05 §Keys); only the public component is ever
published at `/oauth2/jwks`. Rotation keeps recently-retired keys `retired` until in-flight tokens expire.

```sql
-- schema: global
CREATE TABLE signing_key (
    id                  uuid          NOT NULL DEFAULT uuid_generate_v7(),
    kid                 varchar(80)   NOT NULL,
    algorithm           varchar(20)   NOT NULL DEFAULT 'RS256',
    public_jwk          jsonb         NOT NULL,
    private_key_enc     bytea         NOT NULL,
    status              varchar(20)   NOT NULL DEFAULT 'active',
    not_before          timestamptz   NOT NULL DEFAULT now(),
    retired_at          timestamptz   NULL,
    criado_em           timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT ux_signing_key_kid    UNIQUE (kid),
    CONSTRAINT ck_signing_key_status CHECK (status IN ('active','retired')),
    CONSTRAINT ck_signing_key_jwk_object CHECK (jsonb_typeof(public_jwk) = 'object')
);

CREATE UNIQUE INDEX ux_signing_key_single_active ON signing_key (status) WHERE status = 'active';
CREATE INDEX ix_signing_key_status ON signing_key (status);

COMMENT ON TABLE signing_key IS 'ACS: JWT signing keys; only public_jwk is published at /oauth2/jwks (ADR-005).';
COMMENT ON COLUMN signing_key.private_key_enc IS 'Encrypted private key material; never exposed via any API.';
```

**Invariants:**
- THE SYSTEM SHALL keep at most one `status = 'active'` signing key at a time
  (`ux_signing_key_single_active`).
- THE SYSTEM SHALL publish `public_jwk` of the active key plus every `retired` key still within a valid
  token's TTL at `/oauth2/jwks`.
- THE SYSTEM SHALL never expose `private_key_enc` through any API, log, or error body.

### 2.3 `cuidador` [empresa]

A person with an account and a role who may access a tenant's data. Tenant-scoped: an account is born
into exactly one tenant and never moves. `credential_hash` is bcrypt/argon2 and never null; `login` is
unique per tenant among `active` accounts so a deactivated login can be re-issued later.

```sql
-- schema: empresa
CREATE TABLE cuidador (
    id                          uuid          NOT NULL DEFAULT uuid_generate_v7(),
    tenant_id                   uuid          NOT NULL,
    nome                        varchar(150)  NOT NULL,
    login                       varchar(150)  NOT NULL,
    credential_hash             varchar(200)  NOT NULL,
    papel                       varchar(30)   NOT NULL,
    status                      varchar(20)   NOT NULL DEFAULT 'active',
    criado_em                   timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id       uuid          NULL,
    atualizado_em               timestamptz   NOT NULL DEFAULT now(),
    atualizado_por_usuario_id   uuid          NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_cuidador_papel  CHECK (papel IN ('cuidadora','coordenador_clinica')),
    CONSTRAINT ck_cuidador_status CHECK (status IN ('active','inactive'))
);

CREATE UNIQUE INDEX ux_cuidador_login_active
    ON cuidador (tenant_id, lower(login))
    WHERE status = 'active';

CREATE INDEX ix_cuidador_tenant ON cuidador (tenant_id);
CREATE INDEX ix_cuidador_papel  ON cuidador (tenant_id, papel);

COMMENT ON TABLE cuidador IS 'ACS: caregiver account, tenant-scoped; identity + access metadata only (LGPD).';
COMMENT ON COLUMN cuidador.credential_hash IS 'bcrypt/argon2 hash; never cleartext, never returned by any API.';
COMMENT ON COLUMN cuidador.tenant_id IS 'Logical FK to global tenant.id (PS.003, no physical cross-schema FK).';
```

**Invariants:**
- THE SYSTEM SHALL store `credential_hash` non-null and hashed; IF a create/update supplies no derivable
  hash, THEN THE SYSTEM SHALL reject the write (`NOT NULL`) — invariant #3.
- IF an `active` caregiver with the same `lower(login)` exists in the tenant, THEN THE SYSTEM SHALL reject
  the insert (`ux_cuidador_login_active`) — API `409 login-duplicate`.
- IF `papel` is not `cuidadora | coordenador_clinica`, THEN THE SYSTEM SHALL reject the write
  (`ck_cuidador_papel`) — keeps the `role → authorities` map total (invariant #4).
- THE SYSTEM SHALL never hard-delete a caregiver; deactivation transitions `status` to `inactive`
  (invariant #7).

### 2.4 `caregiver_patient_link` [empresa]

The record that a caregiver is responsible for a patient — the authoritative routing/scope source
(ADR-006, the anchor relationship). Tenant-scoped. `paciente_id` is a **logical** reference to a
PAC-owned `Paciente`, validated at link time via `PacPatientResolvePort`. At most one `active` link per
(`cuidador_id`, `paciente_id`).

```sql
-- schema: empresa
CREATE TABLE caregiver_patient_link (
    id                          uuid          NOT NULL DEFAULT uuid_generate_v7(),
    tenant_id                   uuid          NOT NULL,
    cuidador_id                 uuid          NOT NULL,
    paciente_id                 uuid          NOT NULL,
    status                      varchar(20)   NOT NULL DEFAULT 'active',
    criado_em                   timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id       uuid          NULL,
    atualizado_em               timestamptz   NOT NULL DEFAULT now(),
    atualizado_por_usuario_id   uuid          NULL,
    revogado_em                 timestamptz   NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_link_status CHECK (status IN ('active','revoked')),
    CONSTRAINT fk_link_cuidador
        FOREIGN KEY (cuidador_id) REFERENCES cuidador (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX ux_link_active
    ON caregiver_patient_link (cuidador_id, paciente_id)
    WHERE status = 'active';

CREATE INDEX ix_link_paciente ON caregiver_patient_link (tenant_id, paciente_id) WHERE status = 'active';
CREATE INDEX ix_link_cuidador ON caregiver_patient_link (cuidador_id, status);

COMMENT ON TABLE caregiver_patient_link IS 'ACS: caregiver-patient linkage; single source of alarm routing + data-scope (ADR-006).';
COMMENT ON COLUMN caregiver_patient_link.paciente_id IS 'Logical FK to PAC Paciente (PS.003); validated at link time, not a DB FK.';
```

**Invariants:**
- THE SYSTEM SHALL confine `cuidador_id` and `paciente_id` to the **same** tenant as the row's
  `tenant_id`; the same-tenant check is enforced in the application service before insert (cross-schema
  `paciente_id` cannot carry a physical FK) — IF caregiver and patient differ in tenant, THEN THE SYSTEM
  SHALL reject with `422 cross-tenant-link` (invariant #1).
- IF an `active` link already exists for (`cuidador_id`, `paciente_id`), THEN THE SYSTEM SHALL treat a
  new link request as idempotent and SHALL NOT create a duplicate (`ux_link_active`).
- WHEN a link is revoked, THE SYSTEM SHALL set `status = revoked` and `revogado_em = now()` (soft) and
  SHALL preserve the row for HIST audit interpretation (invariant #7).
- THE index `ix_link_paciente` (partial on `status = 'active'`) SHALL back the emergency routing resolve
  (§6.2 of api-contract) so "who receives this patient's alarm" is an indexed lookup.

---

## 3. Relations (ASCII)

```
tenant (global) 1 ──< cuidador (empresa)                 [logical FK tenant_id, PS.003]
tenant (global) 1 ──< caregiver_patient_link (empresa)   [logical FK tenant_id, PS.003]
cuidador 1 ──< caregiver_patient_link                    [physical FK cuidador_id, same schema]
caregiver_patient_link  >── paciente (PAC)               [logical FK paciente_id, validated via PAC]
signing_key (global)    — no FK; aggregated into the JWK set
```

Not stored here (owned by peers, referenced by id only): `Paciente` (PAC), `Evento` (EVNT),
`DispositivoEdge` (EDGE), `TipoGesto`/`Comando` (GEST/PAC). No biometric/clinical column exists in any
ACS table (LGPD hard rule).

---

## 4. Schema-level mapping

| Table | Schema |
|---|---|
| `tenant` | `global` (`neuroassist_global`, once) |
| `signing_key` | `global` (`neuroassist_global`, once) |
| `cuidador` | `empresa` (per-tenant `t_<tenantId>`) |
| `caregiver_patient_link` | `empresa` (per-tenant `t_<tenantId>`) |
| `uuid-ossp`, `pgcrypto` extensions | `global` (once) |

---

## 5. Migration plan (MIG-owned)

ACS ships **no** local Flyway; it contributes scripts to the centralized MIG set (platform-patterns §3).
Because ACS straddles both tiers it reserves **two** non-colliding bands:

- **Global band `V0200..V0249`** — `V0200__acs_global_identity_schema.sql` creates `tenant` and
  `signing_key` in `neuroassist_global` (extensions first). Applied **once**.
- **Empresa band `V0250..V0299`** — `V0250__acs_initial_schema.sql` creates `cuidador` and
  `caregiver_patient_link` in **every** `t_<tenantId>` schema; also applied to each newly provisioned
  schema at runtime (ADR-004 provisioning call).

These bands are non-colliding with EVNT (`V0001..V0099`), GEST (`V0400..V0499`), and EDGE
(`V0700..V0799`). Rules: extensions first; each table's `CREATE` + its indexes/constraints stay in one
block to preserve order; never edit an applied migration — future evolution uses new `V02xx` scripts.
