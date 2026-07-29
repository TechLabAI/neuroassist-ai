-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   pac-service
-- Generated: 2026-07-28 by be-migration-generator.py
-- Source:    contracts/data-model.md (extraído de 6 blocos ```sql```)
--
-- ATENÇÃO: este arquivo é GERADO. Para alterar DDL:
--   1. Edite contracts/data-model.md (blocos ```sql```).
--   2. Re-rode: python scripts/be-migration-generator.py <service> --spec-dir <path> --force
-- =============================================================================

-- schema: global
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- schema: empresa
CREATE TABLE paciente (
    id                    uuid         NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id             uuid         NOT NULL,
    nome                  text         NOT NULL,
    dispositivo_edge_id   uuid         NULL,
    row_version           integer      NOT NULL DEFAULT 0,
    created_at            timestamptz  NOT NULL DEFAULT now(),
    updated_at            timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CONSTRAINT ck_paciente_nome_nonempty CHECK (length(btrim(nome)) > 0)
);

CREATE INDEX ix_paciente_tenant ON paciente (tenant_id);
CREATE INDEX ix_paciente_dispositivo ON paciente (dispositivo_edge_id);
COMMENT ON TABLE paciente IS 'PAC-owned patient (system of record). Tenant data, per-empresa schema.';
COMMENT ON COLUMN paciente.dispositivo_edge_id IS 'Cross-service reference to EDGE DispositivoEdge (not a FK).';

-- schema: empresa
CREATE TABLE paciente_cuidador (
    paciente_id             uuid        NOT NULL,
    cuidador_responsavel_id uuid        NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (paciente_id, cuidador_responsavel_id),
    FOREIGN KEY (paciente_id) REFERENCES paciente (id) ON DELETE CASCADE
);

CREATE INDEX ix_pac_cuidador_paciente ON paciente_cuidador (paciente_id);
COMMENT ON TABLE paciente_cuidador IS 'Responsible caregivers (ACS Cuidador ids, cross-service refs).';

-- schema: empresa
CREATE TABLE comando (
    id                     uuid         NOT NULL DEFAULT uuid_generate_v4(),
    paciente_id            uuid         NOT NULL,
    rotulo                 text         NOT NULL,
    necessidade            text         NOT NULL,
    primitivo_principal_id uuid         NOT NULL,
    primitivo_secundario_id uuid        NULL,
    sensibilidade          text         NOT NULL,
    tempo_min_gesto_ms     integer      NOT NULL,
    tempo_max_gesto_ms     integer      NOT NULL,
    urgencia               text         NOT NULL,
    estado                 text         NOT NULL DEFAULT 'drafted',
    calibracao_versao      integer      NOT NULL DEFAULT 0,
    row_version            integer      NOT NULL DEFAULT 0,
    created_at             timestamptz  NOT NULL DEFAULT now(),
    updated_at             timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    FOREIGN KEY (paciente_id) REFERENCES paciente (id) ON DELETE RESTRICT,
    CONSTRAINT ck_comando_rotulo_nonempty CHECK (length(btrim(rotulo)) > 0),
    CONSTRAINT ck_comando_necessidade_nonempty CHECK (length(btrim(necessidade)) > 0),
    CONSTRAINT ck_comando_sensibilidade CHECK (sensibilidade IN ('baixa','media','alta')),
    CONSTRAINT ck_comando_urgencia CHECK (urgencia IN ('emergencia','rotina')),
    CONSTRAINT ck_comando_estado CHECK (estado IN ('drafted','validated','published','retired')),
    CONSTRAINT ck_comando_timing CHECK (tempo_min_gesto_ms > 0 AND tempo_min_gesto_ms <= tempo_max_gesto_ms),
    CONSTRAINT ck_comando_distinct_primitives CHECK (
        primitivo_secundario_id IS NULL OR primitivo_secundario_id <> primitivo_principal_id
    )
);

CREATE INDEX ix_comando_paciente ON comando (paciente_id);
CREATE INDEX ix_comando_paciente_estado ON comando (paciente_id, estado);

-- One active mapping (gesture-set + need) per patient; retired rows are exempt so history is preserved.
CREATE UNIQUE INDEX ux_comando_active_mapping
    ON comando (paciente_id, primitivo_principal_id, coalesce(primitivo_secundario_id, '00000000-0000-0000-0000-000000000000'::uuid), necessidade)
    WHERE estado <> 'retired';

COMMENT ON TABLE comando IS 'PAC-owned command catalog item. Per-command calibration (ADR-004). History-preserving (ADR-006).';
COMMENT ON COLUMN comando.primitivo_principal_id IS 'Cross-service reference to GEST TipoGesto (not a FK).';
COMMENT ON COLUMN comando.calibracao_versao IS 'Monotonic; +1 on each in-place recalibration of a published command.';

-- schema: empresa
CREATE TABLE tipo_gesto_ref (
    tipo_gesto_id  uuid         NOT NULL,
    nome           text         NOT NULL,
    supported      boolean      NOT NULL DEFAULT false,
    synced_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tipo_gesto_id)
);

COMMENT ON TABLE tipo_gesto_ref IS 'Read-only projection of GEST TipoGesto (display cache). NOT authoritative for the emergency gate.';

-- schema: empresa
CREATE OR REPLACE FUNCTION pac_touch_row() RETURNS trigger AS $$
BEGIN
    NEW.updated_at := now();
    NEW.row_version := OLD.row_version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER tg_paciente_touch BEFORE UPDATE ON paciente
    FOR EACH ROW EXECUTE FUNCTION pac_touch_row();
CREATE TRIGGER tg_comando_touch BEFORE UPDATE ON comando
    FOR EACH ROW EXECUTE FUNCTION pac_touch_row();
