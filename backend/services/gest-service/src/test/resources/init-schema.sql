-- init-schema.sql — executado pelo PostgreSQLContainer antes dos testes
-- Cria schemas de tenant de teste e objetos GEST para o serviço gest-service.
-- P3 update: adiciona schema t_emp_other (para testes de isolamento cross-tenant)
-- e cria as tabelas GEST em cada schema de tenant para garantir isolação real via search_path.

-- ────────────────────────────────────────────────────────────────────────────
-- Extensions required by the architecture (persistence.arch.json, D-068).
-- ────────────────────────────────────────────────────────────────────────────
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "pg_trgm";
CREATE EXTENSION IF NOT EXISTS "unaccent";

-- ────────────────────────────────────────────────────────────────────────────
-- Tenant schemas for integration tests
-- t_emp_test  — primary test tenant
-- t_emp_other — secondary tenant for cross-tenant isolation tests (INT-GEST-005)
-- ────────────────────────────────────────────────────────────────────────────
CREATE SCHEMA IF NOT EXISTS t_emp_test;
CREATE SCHEMA IF NOT EXISTS t_emp_other;

GRANT ALL PRIVILEGES ON SCHEMA t_emp_test  TO test;
GRANT ALL PRIVILEGES ON SCHEMA t_emp_other TO test;

-- ────────────────────────────────────────────────────────────────────────────
-- GEST tables in t_emp_test
-- These take priority over any Hibernate create-drop tables in public schema
-- when search_path = t_emp_test, public (schema-first resolution).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS t_emp_test.tipo_gesto (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    nome                 varchar(120)  NOT NULL,
    detectability_state  varchar(20)   NOT NULL DEFAULT 'registered',
    engine_feature_ref   varchar(200)  NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           varchar(120)  NULL,
    updated_by           varchar(120)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_tgt_state CHECK (detectability_state IN ('registered','pending_training','supported','deprecated')),
    CONSTRAINT ck_tgt_supported_needs_ref CHECK (detectability_state <> 'supported' OR engine_feature_ref IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tgt_nome_active
    ON t_emp_test.tipo_gesto (lower(nome))
    WHERE detectability_state <> 'deprecated';

CREATE TABLE IF NOT EXISTS t_emp_test.tipo_gesto_transition (
    id             uuid         NOT NULL DEFAULT gen_random_uuid(),
    tipo_gesto_id  uuid         NOT NULL,
    from_state     varchar(20)  NULL,
    to_state       varchar(20)  NOT NULL,
    actor          varchar(120) NOT NULL,
    reason         varchar(500) NULL,
    at             timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT fk_tgtr_tipo FOREIGN KEY (tipo_gesto_id) REFERENCES t_emp_test.tipo_gesto (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS t_emp_test.detection_profile (
    id                    uuid         NOT NULL DEFAULT gen_random_uuid(),
    version               bigint       NOT NULL,
    active                boolean      NOT NULL DEFAULT false,
    contract_version      varchar(20)  NOT NULL,
    classification_params jsonb        NOT NULL,
    published_at          timestamptz  NOT NULL DEFAULT now(),
    published_by          varchar(120) NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_dp_version UNIQUE (version),
    CONSTRAINT ck_dp_params CHECK (jsonb_typeof(classification_params) = 'object')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_dp_single_active
    ON t_emp_test.detection_profile (active) WHERE active = true;

CREATE TABLE IF NOT EXISTS t_emp_test.detection_profile_primitive (
    id                    uuid         NOT NULL DEFAULT gen_random_uuid(),
    detection_profile_id  uuid         NOT NULL,
    tipo_gesto_id         uuid         NOT NULL,
    nome_snapshot         varchar(120) NOT NULL,
    engine_feature_ref    varchar(200) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_dpp_profile FOREIGN KEY (detection_profile_id) REFERENCES t_emp_test.detection_profile (id) ON DELETE CASCADE,
    CONSTRAINT fk_dpp_tipo    FOREIGN KEY (tipo_gesto_id) REFERENCES t_emp_test.tipo_gesto (id) ON DELETE RESTRICT,
    CONSTRAINT ux_dpp UNIQUE (detection_profile_id, tipo_gesto_id)
);

CREATE TABLE IF NOT EXISTS t_emp_test.processed_events (
    event_id      uuid         NOT NULL,
    event_type    varchar(100) NOT NULL,
    processed_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id)
);

GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA t_emp_test TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA t_emp_test TO test;

-- ────────────────────────────────────────────────────────────────────────────
-- GEST tables in t_emp_other (for cross-tenant isolation tests)
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS t_emp_other.tipo_gesto (
    id                   uuid          NOT NULL DEFAULT gen_random_uuid(),
    nome                 varchar(120)  NOT NULL,
    detectability_state  varchar(20)   NOT NULL DEFAULT 'registered',
    engine_feature_ref   varchar(200)  NULL,
    created_at           timestamptz   NOT NULL DEFAULT now(),
    updated_at           timestamptz   NOT NULL DEFAULT now(),
    created_by           varchar(120)  NULL,
    updated_by           varchar(120)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_tgo_state CHECK (detectability_state IN ('registered','pending_training','supported','deprecated')),
    CONSTRAINT ck_tgo_supported_needs_ref CHECK (detectability_state <> 'supported' OR engine_feature_ref IS NOT NULL)
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_tgo_nome_active
    ON t_emp_other.tipo_gesto (lower(nome))
    WHERE detectability_state <> 'deprecated';

CREATE TABLE IF NOT EXISTS t_emp_other.tipo_gesto_transition (
    id             uuid         NOT NULL DEFAULT gen_random_uuid(),
    tipo_gesto_id  uuid         NOT NULL,
    from_state     varchar(20)  NULL,
    to_state       varchar(20)  NOT NULL,
    actor          varchar(120) NOT NULL,
    reason         varchar(500) NULL,
    at             timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT fk_tgtr_o_tipo FOREIGN KEY (tipo_gesto_id) REFERENCES t_emp_other.tipo_gesto (id) ON DELETE RESTRICT
);

CREATE TABLE IF NOT EXISTS t_emp_other.detection_profile (
    id                    uuid         NOT NULL DEFAULT gen_random_uuid(),
    version               bigint       NOT NULL,
    active                boolean      NOT NULL DEFAULT false,
    contract_version      varchar(20)  NOT NULL,
    classification_params jsonb        NOT NULL,
    published_at          timestamptz  NOT NULL DEFAULT now(),
    published_by          varchar(120) NULL,
    PRIMARY KEY (id),
    CONSTRAINT ux_dpo_version UNIQUE (version),
    CONSTRAINT ck_dpo_params CHECK (jsonb_typeof(classification_params) = 'object')
);

CREATE UNIQUE INDEX IF NOT EXISTS ux_dpo_single_active
    ON t_emp_other.detection_profile (active) WHERE active = true;

CREATE TABLE IF NOT EXISTS t_emp_other.detection_profile_primitive (
    id                    uuid         NOT NULL DEFAULT gen_random_uuid(),
    detection_profile_id  uuid         NOT NULL,
    tipo_gesto_id         uuid         NOT NULL,
    nome_snapshot         varchar(120) NOT NULL,
    engine_feature_ref    varchar(200) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_dppo_profile FOREIGN KEY (detection_profile_id) REFERENCES t_emp_other.detection_profile (id) ON DELETE CASCADE,
    CONSTRAINT fk_dppo_tipo    FOREIGN KEY (tipo_gesto_id) REFERENCES t_emp_other.tipo_gesto (id) ON DELETE RESTRICT,
    CONSTRAINT ux_dppo UNIQUE (detection_profile_id, tipo_gesto_id)
);

CREATE TABLE IF NOT EXISTS t_emp_other.processed_events (
    event_id      uuid         NOT NULL,
    event_type    varchar(100) NOT NULL,
    processed_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id)
);

GRANT ALL PRIVILEGES ON ALL TABLES    IN SCHEMA t_emp_other TO test;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA t_emp_other TO test;
