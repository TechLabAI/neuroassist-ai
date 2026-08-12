-- GEST migration — faixa V0400..V0499 — owner: gest-service
-- =============================================================================
-- Migration: V0400__gest_initial_schema.sql
-- Service:   gest-service
-- Schema:    empresa (per-tenant, applied by Flyway per-tenant schema run)
-- Based on:  gest-service/src/main/resources/db/migration/V1__create_initial_schema.sql
--
-- Este arquivo é a contribuição do gest-service ao neuroassist-ai-migrations.
-- Todos os objetos aqui criados são tenant-scoped — aplicados no schema t_<tenantId>.
-- NUNCA hardcode schema — o Flyway aplica este arquivo dentro do schema correto.
-- =============================================================================

-- Extensão necessária para gen_random_uuid() — deve existir no schema global antes
-- (V0001 cria neuroassist_global; a extensão pgcrypto é global, não por-schema).
-- Não repetir CREATE EXTENSION aqui — é idempotente no global e pode causar conflito.

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: tipo_gesto
-- Primitivo facial detectável (catálogo extensível, ADR-006).
-- Isolamento: sem coluna tenant_id — isolação via search_path (schema routing).
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: tipo_gesto_transition
-- Append-only audit log de transições de estado (invariante #4).
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: detection_profile
-- Perfil de detecção versionado e imutável puxado pelo edge (invariantes #2, #3).
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: detection_profile_primitive
-- Snapshot de primitivos SUPPORTED embutidos num profile (join imutável).
-- ────────────────────────────────────────────────────────────────────────────
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

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: processed_events
-- Idempotência de mensagens JMS — um registro por event_id processado.
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE processed_events (
    event_id      uuid         NOT NULL,
    event_type    varchar(100) NOT NULL,
    processed_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (event_id)
);

COMMENT ON TABLE processed_events IS 'GEST: idempotency fence — one row per processed event id (D-065).';
