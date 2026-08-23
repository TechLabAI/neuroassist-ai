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
