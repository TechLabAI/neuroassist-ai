-- init-schema.sql — executado pelo PostgreSQLContainer antes dos testes
-- Cria schema de tenant de teste para o servico edge-service

-- Extensions required by the architecture (persistence.arch.json, D-068).
-- Mirror the production V0001 so tests fail fast when an extension is missing.
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";

-- Tenant schema for BDD/integration tests.
-- Production uses schema names like 't_emp_dev'; tests use 't_emp_test'.
CREATE SCHEMA IF NOT EXISTS t_emp_test;

GRANT ALL PRIVILEGES ON SCHEMA t_emp_test TO test;

-- -------------------------------------------------------------------------
-- Set search_path so subsequent DDL lands in t_emp_test without qualification.
-- -------------------------------------------------------------------------
SET search_path = t_emp_test, public;

-- Aggregate root: edge device identity, pairing, and liveness inputs.
-- Mirrors V0700__edge_initial_schema.sql (empresa scope).
CREATE TABLE IF NOT EXISTS t_emp_test.dispositivo_edge (
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

CREATE UNIQUE INDEX IF NOT EXISTS ux_dispositivo_edge_active_pairing
    ON t_emp_test.dispositivo_edge (paciente_id)
    WHERE pairing_state = 'paired';

CREATE INDEX IF NOT EXISTS ix_dispositivo_edge_paciente
    ON t_emp_test.dispositivo_edge (paciente_id);
CREATE INDEX IF NOT EXISTS ix_dispositivo_edge_state
    ON t_emp_test.dispositivo_edge (pairing_state);
CREATE INDEX IF NOT EXISTS ix_dispositivo_edge_sync
    ON t_emp_test.dispositivo_edge (ultima_sincronizacao);

-- Enrollment tokens (single-use, TTL-bounded). FK -> dispositivo_edge.
CREATE TABLE IF NOT EXISTS t_emp_test.dispositivo_enrollment (
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
        FOREIGN KEY (dispositivo_edge_id)
        REFERENCES t_emp_test.dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_enrollment_device
    ON t_emp_test.dispositivo_enrollment (dispositivo_edge_id);

-- Per-device OAuth2 credential (hash only). FK -> dispositivo_edge.
CREATE TABLE IF NOT EXISTS t_emp_test.dispositivo_credential (
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
        FOREIGN KEY (dispositivo_edge_id)
        REFERENCES t_emp_test.dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_dispositivo_credential_active
    ON t_emp_test.dispositivo_credential (dispositivo_edge_id)
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS ix_credential_device
    ON t_emp_test.dispositivo_credential (dispositivo_edge_id);

-- Append-only pairing + liveness transition audit. FK -> dispositivo_edge.
CREATE TABLE IF NOT EXISTS t_emp_test.dispositivo_edge_transition (
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
        FOREIGN KEY (dispositivo_edge_id)
        REFERENCES t_emp_test.dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_transition_device
    ON t_emp_test.dispositivo_edge_transition (dispositivo_edge_id, at);

-- Restore default search_path.
SET search_path = public;

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA t_emp_test TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA t_emp_test TO test;

-- -------------------------------------------------------------------------
-- Second tenant schema: t_emp_other (tenant UUID 00000000-0000-0000-0000-000000000002).
-- Used exclusively by MultiTenantIsolationIT to verify cross-tenant data isolation.
-- Structure mirrors t_emp_test / V0700__edge_initial_schema.sql exactly.
-- -------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS t_emp_other;

GRANT ALL PRIVILEGES ON SCHEMA t_emp_other TO test;

CREATE TABLE IF NOT EXISTS t_emp_other.dispositivo_edge (
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
    CONSTRAINT ck_dispositivo_edge_other_state
        CHECK (pairing_state IN ('registered','paired','unpaired','retired')),
    CONSTRAINT ck_dispositivo_edge_other_paired_has_patient
        CHECK (pairing_state <> 'paired' OR paciente_id IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_dispositivo_edge_other_active_pairing
    ON t_emp_other.dispositivo_edge (paciente_id)
    WHERE pairing_state = 'paired';

CREATE INDEX IF NOT EXISTS ix_dispositivo_edge_other_paciente
    ON t_emp_other.dispositivo_edge (paciente_id);
CREATE INDEX IF NOT EXISTS ix_dispositivo_edge_other_state
    ON t_emp_other.dispositivo_edge (pairing_state);
CREATE INDEX IF NOT EXISTS ix_dispositivo_edge_other_sync
    ON t_emp_other.dispositivo_edge (ultima_sincronizacao);

CREATE TABLE IF NOT EXISTS t_emp_other.dispositivo_enrollment (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            varchar(60)   NOT NULL,
    dispositivo_edge_id  uuid          NOT NULL,
    token_hash           varchar(200)  NOT NULL,
    expires_at           timestamptz   NOT NULL,
    consumed_at          timestamptz   NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT ux_dispositivo_enrollment_other_token UNIQUE (token_hash),
    CONSTRAINT fk_enrollment_other_device
        FOREIGN KEY (dispositivo_edge_id)
        REFERENCES t_emp_other.dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_enrollment_other_device
    ON t_emp_other.dispositivo_enrollment (dispositivo_edge_id);

CREATE TABLE IF NOT EXISTS t_emp_other.dispositivo_credential (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    tenant_id            varchar(60)   NOT NULL,
    dispositivo_edge_id  uuid          NOT NULL,
    client_id            varchar(120)  NOT NULL,
    secret_hash          varchar(200)  NOT NULL,
    status               varchar(20)   NOT NULL DEFAULT 'active',
    issued_at            timestamptz   NOT NULL DEFAULT now(),
    revoked_at           timestamptz   NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_dispositivo_credential_other_client UNIQUE (client_id),
    CONSTRAINT ck_dispositivo_credential_other_status CHECK (status IN ('active','revoked')),
    CONSTRAINT fk_credential_other_device
        FOREIGN KEY (dispositivo_edge_id)
        REFERENCES t_emp_other.dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_dispositivo_credential_other_active
    ON t_emp_other.dispositivo_credential (dispositivo_edge_id)
    WHERE status = 'active';

CREATE INDEX IF NOT EXISTS ix_credential_other_device
    ON t_emp_other.dispositivo_credential (dispositivo_edge_id);

CREATE TABLE IF NOT EXISTS t_emp_other.dispositivo_edge_transition (
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
    CONSTRAINT ck_transition_other_kind CHECK (kind IN ('pairing','liveness')),
    CONSTRAINT fk_transition_other_device
        FOREIGN KEY (dispositivo_edge_id)
        REFERENCES t_emp_other.dispositivo_edge (id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_transition_other_device
    ON t_emp_other.dispositivo_edge_transition (dispositivo_edge_id, at);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA t_emp_other TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA t_emp_other TO test;

-- -------------------------------------------------------------------------
-- Global schema: tenant catalogue + cross-tenant lookup indices (C-18, C-21).
-- All types mirror production migrations exactly — no intentional divergence.
-- tenant_id is uuid in all global tables, matching V0100/V0700/V0701.
-- -------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS neuroassist_global;

GRANT ALL PRIVILEGES ON SCHEMA neuroassist_global TO test;

-- Tenant catalogue: mirrors neuroassist_global.tenant from V0100__acs_global_schema.sql.
-- Required so MultiTenantConnectionProvider.resolveSchema() exercises the real catalogue
-- path (tenant UUID → schema_nome) instead of falling back to the identifier-as-schema.
CREATE TABLE IF NOT EXISTS neuroassist_global.tenant (
    tenant_id                 uuid          NOT NULL DEFAULT gen_random_uuid(),
    nome                      varchar(200)  NOT NULL,
    tipo                      varchar(50)   NOT NULL,
    status                    varchar(50)   NOT NULL DEFAULT 'PROVISIONING',
    schema_nome               varchar(80)   NULL,
    criado_em                 timestamptz   NOT NULL DEFAULT now(),
    criado_por_usuario_id     uuid          NULL,
    atualizado_em             timestamptz   NULL,
    atualizado_por_usuario_id uuid          NULL,
    PRIMARY KEY (tenant_id),
    CONSTRAINT ck_tenant_tipo   CHECK (tipo   IN ('CLINICA','PAC_HOME','TELEHEALTH')),
    CONSTRAINT ck_tenant_status CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED'))
);

-- Test tenant: UUID 00000000-0000-0000-0000-000000000001 → schema t_emp_test.
-- TestJwtFactory.TEST_TENANT carries this UUID in all BDD JWTs.
-- Test tenant B: UUID 00000000-0000-0000-0000-000000000002 → schema t_emp_other.
-- Used by MultiTenantIsolationIT to prove UUID→schema catalogue routing (not fallback).
-- The UUID differs from the schema name, so a broken provider that uses the UUID as the
-- schema name directly would fail — only catalogue lookup produces the right schema.
INSERT INTO neuroassist_global.tenant
    (tenant_id, nome, tipo, status, schema_nome)
VALUES
    ('00000000-0000-0000-0000-000000000001', 'Test Tenant',       'CLINICA', 'ACTIVE', 't_emp_test'),
    ('00000000-0000-0000-0000-000000000002', 'Other Test Tenant', 'CLINICA', 'ACTIVE', 't_emp_other');

-- Enrollment directory: token_hash → tenant (C-21).
-- Mirrors V0700__edge_enrollment_directory.sql — tenant_id is uuid, NOT varchar.
-- EnrollmentDirectoryJdbcAdapter uses ?::uuid cast to pass the UUID string parameter.
CREATE TABLE IF NOT EXISTS neuroassist_global.enrollment_directory (
    token_hash           varchar(200) NOT NULL,
    tenant_id            uuid         NOT NULL,
    dispositivo_edge_id  uuid         NOT NULL,
    expires_at           timestamptz  NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (token_hash)
);

-- Device directory: client_id → tenant (C-18).
-- Mirrors V0701__edge_device_directory.sql — tenant_id is uuid, NOT varchar.
-- DeviceDirectoryJdbcAdapter uses ?::uuid cast to pass the UUID string parameter.
CREATE TABLE IF NOT EXISTS neuroassist_global.device_directory (
    client_id            varchar(120) NOT NULL,
    tenant_id            uuid         NOT NULL,
    dispositivo_edge_id  uuid         NOT NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (client_id)
);

GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA neuroassist_global TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA neuroassist_global TO test;
