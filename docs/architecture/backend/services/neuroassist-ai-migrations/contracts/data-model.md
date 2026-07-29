# MIG — Data Model

> **MIG owns no business table.** It is the component that operates on every
> schema (doc 05). Its durable artifacts are (a) the Flyway history each
> schema carries, (b) the **global tenant registry** it migrates as part of
> the global baseline, and (c) the schema **topology** it provisions. This
> file is authoritative about the topology and the registry — not about the
> business entities the tenant template creates, which remain owned
> conceptually by PAC/ACS/HIST/EVNT/EDGE (their `contracts/data-model.md`
> will be the binding source once those modules go through `/be-architect-specs`).

## 1. Conventions

- **Schema naming:** the global schema is the fixed literal
  `neuroassist_global`; tenant schemas are `t_<tenantId>` where `tenantId` is
  the UUID primary key of the `tenant` row, with dashes stripped
  (`t_b3f1a2c4...`) so the schema name is a valid unquoted PostgreSQL
  identifier.
- **Versioning:** forward-only; `V<NNNN>__<description>.sql`. Global and
  tenant-template migrations are separate Flyway locations, each with its own
  monotonic version sequence (no shared numbering).
- **History:** `flyway_schema_history` is created inside each target schema
  so every schema's ledger is self-contained and re-runs are idempotent.
- **PK strategy (tenant-template tables):** UUID primary keys, `created_at`/
  `updated_at` timestamptz, no hard deletes on audit-relevant tables (HIST).
- **Ordering invariant:** the global schema must be at head before any
  tenant provisioning runs (global → tenant, doc 05 §Migration Strategy).

## 0. Extensions

```sql
-- schema: global
-- MIG global baseline V0001 — platform extensions.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
```

**Invariants (EARS):**
`THE SYSTEM SHALL create the platform extensions idempotently (IF NOT EXISTS) in the global schema only.`
`THE SYSTEM SHALL create the global extensions before applying any tenant DDL that depends on them.`

## 2. Schema topology (what MIG provisions)

### 2.1 `neuroassist_global` [global]

The single global schema: the **tenant registry** (list of empresas + their
schema names + tipo) and any platform-wide reference data. MIG creates the
schema container and applies the global migration set to it.

```sql
-- schema: global
CREATE SCHEMA IF NOT EXISTS neuroassist_global;
COMMENT ON SCHEMA neuroassist_global IS 'Global schema (tenant registry, cross-tenant reference data). Provisioned and migrated by neuroassist-ai-migrations (MIG).';
```

### 2.2 `tenant` [global] — the registry MIG fans out over

```sql
-- schema: global
CREATE TABLE IF NOT EXISTS tenant (
    id          UUID        NOT NULL DEFAULT uuid_generate_v4(),
    nome        VARCHAR(200) NOT NULL,
    tipo        VARCHAR(20) NOT NULL,
    schema_name VARCHAR(63) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (schema_name),
    CHECK (tipo IN ('familia', 'clinica'))
);

CREATE INDEX IF NOT EXISTS idx_tenant_tipo ON tenant (tipo);
COMMENT ON TABLE tenant IS 'Registry of every empresa (família ou clínica); schema_name is the physical t_<tenantId> schema MIG provisions and migrates. Iterated by MIG at migrate-all/fan-out time.';
```

**Invariants (EARS):**
`THE SYSTEM SHALL derive the set of tenant schemas to migrate by reading the tenant table in neuroassist_global.`
`THE SYSTEM SHALL keep schema_name unique across all tenant rows.`

### 2.3 `t_<tenantId>` [tenant template]

One schema per empresa, created at onboarding via `provision-tenant`. MIG
creates the schema container and applies the canonical tenant-template
migration set to it — identical for `familia` and `clinica` (A3, to validate
with PAC/ACS once their specs land).

```sql
-- schema: empresa
-- Provisioning template executed by MIG at provision-tenant time (tenantId bound at runtime).
CREATE SCHEMA IF NOT EXISTS t_<tenantId>;
COMMENT ON SCHEMA t_<tenantId> IS 'Tenant schema for one empresa. Provisioned and migrated by neuroassist-ai-migrations (MIG); no automated drop in MVP (G1).';
```

**Invariants (EARS):**
`WHEN a new empresa is provisioned, THE SYSTEM SHALL create its schema and apply the full tenant-template migration set up to the current version before returning success to the caller.`
`THE SYSTEM SHALL apply the identical tenant-template set to every empresa schema regardless of tipo (familia|clinica).`

## 3. Tenant template — business tables MIG runs (not owns)

> Listed as the migration surface per the BA draft data model (doc 05 §Initial
> Entities). The binding source, once each module goes through
> `/be-architect-specs`, will be that module's own `contracts/data-model.md`;
> MIG never authors business DDL beyond this initial baseline (ADR-002).

| Table | Owning module (future) | Notes |
|---|---|---|
| `paciente` | PAC | fk `dispositivo_edge` |
| `cuidador` | ACS | enum `papel` (cuidadora, coordenador_clinica) |
| `tipo_gesto` | PAC | catálogo extensível de primitivos |
| `comando` | PAC | enums `urgencia`, `sensibilidade`; fk primitivo principal/secundário → `tipo_gesto` |
| `evento` | EVNT / HIST | enum `feedback`; fk `paciente`, `comando`, `confirmado_por` (cuidador) |
| `dispositivo_edge` | EDGE | enum `status` (online, offline); fk `paciente` |

```sql
-- schema: empresa
-- Baseline tenant-template migration (V0002 in the tenant-template sequence).
-- Superseded/extended by each owning module's own migrations once specs land (ADR-002).
CREATE TABLE IF NOT EXISTS tipo_gesto (
    id   UUID        NOT NULL DEFAULT uuid_generate_v4(),
    nome VARCHAR(100) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE (nome)
);

CREATE TABLE IF NOT EXISTS dispositivo_edge (
    id         UUID        NOT NULL DEFAULT uuid_generate_v4(),
    status     VARCHAR(20) NOT NULL DEFAULT 'offline',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (status IN ('online', 'offline'))
);

CREATE TABLE IF NOT EXISTS paciente (
    id                  UUID        NOT NULL DEFAULT uuid_generate_v4(),
    nome                VARCHAR(200) NOT NULL,
    dispositivo_edge_id UUID,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    FOREIGN KEY (dispositivo_edge_id) REFERENCES dispositivo_edge (id)
);

ALTER TABLE dispositivo_edge ADD COLUMN IF NOT EXISTS paciente_id UUID;

CREATE TABLE IF NOT EXISTS cuidador (
    id         UUID        NOT NULL DEFAULT uuid_generate_v4(),
    nome       VARCHAR(200) NOT NULL,
    papel      VARCHAR(30) NOT NULL,
    email      VARCHAR(200) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (email),
    CHECK (papel IN ('cuidadora', 'coordenador_clinica'))
);

CREATE TABLE IF NOT EXISTS comando (
    id                   UUID        NOT NULL DEFAULT uuid_generate_v4(),
    paciente_id          UUID        NOT NULL,
    primitivo_principal  UUID        NOT NULL,
    primitivo_secundario UUID,
    rotulo               VARCHAR(200) NOT NULL,
    urgencia             VARCHAR(20) NOT NULL,
    sensibilidade        VARCHAR(20) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    FOREIGN KEY (primitivo_principal) REFERENCES tipo_gesto (id),
    FOREIGN KEY (primitivo_secundario) REFERENCES tipo_gesto (id),
    CHECK (urgencia IN ('emergencia', 'rotina')),
    CHECK (sensibilidade IN ('baixa', 'media', 'alta'))
);

CREATE TABLE IF NOT EXISTS evento (
    id               UUID        NOT NULL DEFAULT uuid_generate_v4(),
    paciente_id      UUID        NOT NULL,
    comando_id       UUID        NOT NULL,
    confirmado_por   UUID,
    feedback         VARCHAR(20) NOT NULL DEFAULT 'nao_avaliado',
    ocorrido_em      TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    FOREIGN KEY (paciente_id) REFERENCES paciente (id),
    FOREIGN KEY (comando_id) REFERENCES comando (id),
    FOREIGN KEY (confirmado_por) REFERENCES cuidador (id),
    CHECK (feedback IN ('nao_avaliado', 'real', 'falso_positivo'))
);

CREATE INDEX IF NOT EXISTS idx_evento_paciente ON evento (paciente_id);
CREATE INDEX IF NOT EXISTS idx_comando_paciente ON comando (paciente_id);
```

**Invariants:** `paciente.dispositivo_edge_id` and `dispositivo_edge.paciente_id`
form a 1:1 pairing to be tightened (unique constraint) once EDGE/PAC specs
land; kept nullable/bidirectional here to avoid a chicken-and-egg FK at
baseline. Enum columns are `CHECK`-constrained, not native Postgres enums, so
each owning module can extend values via a plain migration without an
`ALTER TYPE` lock.

## 4. The Flyway history ledger

Flyway creates and maintains `flyway_schema_history` **inside each target
schema**. MIG does not hand-author this DDL — Flyway emits it — but it is the
durable artifact the LGPD/security audit reads (doc 05 §Data Lifecycle).

| Column | Purpose |
|---|---|
| `installed_rank` | apply order within the schema |
| `version` | migration version (e.g. `0003`) |
| `description`, `type`, `script` | what was applied |
| `checksum` | integrity of the migration file |
| `installed_by`, `installed_on` | who/when (the privileged MIG role) |
| `execution_time`, `success` | duration + pass/fail |

**Invariants (EARS):**
`THE SYSTEM SHALL maintain one flyway_schema_history inside every targeted schema.`
`THE SYSTEM SHALL treat a history row with success=false as a failed migration that blocks further apply until repaired.`
`THE SYSTEM SHALL verify each migration file checksum against history and SHALL fail the schema IF a previously-applied migration's checksum changed.`

## 5. Migration locations and module contribution

MIG packages two independently-versioned Flyway locations:

| Location | Routed to | Contents |
|---|---|---|
| `db/migration/global/` | `neuroassist_global` | extensions baseline + `tenant` registry |
| `db/migration/empresa/` | every `t_<tenantId>` | tenant-template tables (§3) |

Until PAC/ACS/HIST/EVNT/EDGE each go through `/be-architect-specs`, MIG owns
the initial baseline of the tenant template above. Once a module's own specs
land, that module's `contracts/data-model.md` becomes the binding source for
its tables and contributes new versioned files under `db/migration/empresa/`;
MIG continues to run them but authors none of them going forward
(ADR-002). No version-band reservation exists yet (single-service history at
this stage) — revisit if/when concurrent module contribution requires
disjoint ranges.

**Invariants (EARS):**
`THE SYSTEM SHALL apply db/migration/global/ only to neuroassist_global and db/migration/empresa/ only to t_<tenantId> schemas.`
`THE SYSTEM SHALL apply the empresa location in monotonic version order per schema, with outOfOrder=false.`

## 6. Schema-level mapping

| Artifact | Schema |
|---|---|
| extensions (`uuid-ossp`) | `neuroassist_global` |
| `tenant` (registry) | `neuroassist_global` |
| `flyway_schema_history` (global) | `neuroassist_global` |
| `paciente`, `cuidador`, `tipo_gesto`, `comando`, `evento`, `dispositivo_edge` | each `t_<tenantId>` |
| `flyway_schema_history` (tenant) | each `t_<tenantId>` |
| _MIG-owned business table_ | _(none — MIG owns no business table)_ |
