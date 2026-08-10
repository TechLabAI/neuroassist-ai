-- =============================================================================
-- Migration: V1__gest_poc_schema.sql
-- Service:   gest-service
-- Profile:   poc
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS neuroassist_poc;
SET search_path = neuroassist_poc;

CREATE TABLE tipo_gesto (
    id                  VARCHAR(64) PRIMARY KEY,
    nome                VARCHAR(255) NOT NULL,
    detectability_state VARCHAR(50)  NOT NULL,
    engine_feature_ref  VARCHAR(255),
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE tipo_gesto_transition (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tipo_gesto_id   VARCHAR(64)  NOT NULL REFERENCES tipo_gesto(id),
    from_state      VARCHAR(50)  NOT NULL,
    to_state        VARCHAR(50)  NOT NULL,
    actor           VARCHAR(255),
    reason          TEXT,
    at              TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE detection_profile (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    version           INTEGER      NOT NULL,
    active            BOOLEAN      NOT NULL DEFAULT false,
    contract_version  VARCHAR(50)  NOT NULL,
    classification_params JSONB,
    published_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE detection_profile_primitive (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    profile_id        UUID         NOT NULL REFERENCES detection_profile(id),
    tipo_gesto_id     VARCHAR(64)  NOT NULL REFERENCES tipo_gesto(id),
    nome              VARCHAR(255) NOT NULL,
    engine_feature_ref VARCHAR(255)
);
