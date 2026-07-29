-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   gest-service
-- Generated: 2026-07-27 by be-migration-generator.py
-- Source:    contracts/data-model.md (extraído de 5 blocos ```sql```)
--
-- ATENÇÃO: este arquivo é GERADO. Para alterar DDL:
--   1. Edite contracts/data-model.md (blocos ```sql```).
--   2. Re-rode: python scripts/be-migration-generator.py <service> --spec-dir <path> --force
-- =============================================================================

-- schema: global
CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
