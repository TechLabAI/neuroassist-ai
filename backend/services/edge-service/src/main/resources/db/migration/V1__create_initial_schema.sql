-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   edge-service
-- Generated: 2026-07-29 by be-migration-generator.py
-- Source:    contracts/data-model.md (extraído de 5 blocos ```sql```)
--
-- ATENÇÃO: este arquivo é GERADO. Para alterar DDL:
--   1. Edite contracts/data-model.md (blocos ```sql```).
--   2. Re-rode: python scripts/be-migration-generator.py <service> --spec-dir <path> --force
-- =============================================================================

-- schema: global
CREATE EXTENSION IF NOT EXISTS pgcrypto;

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
