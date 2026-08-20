-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0201__pac_comando.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant, applied by Flyway per-tenant schema run)
-- Based on:  pac-service/contracts/data-model.md §2.3
--
-- Tabela comando + índices + índice parcial ux_comando_active_mapping.
-- Pré-requisito: V0200 (tabela paciente deve existir para a FK).
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: comando
-- Item do catálogo: gesto → rótulo/necessidade, calibrado por comando.
-- State machine: drafted → validated → published → retired.
-- History-preserving: retired é terminal, sem hard delete (ADR-006).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE comando (
    id                      uuid         NOT NULL DEFAULT uuid_generate_v4(),
    paciente_id             uuid         NOT NULL,
    rotulo                  text         NOT NULL,
    necessidade             text         NOT NULL,
    primitivo_principal_id  uuid         NOT NULL,
    primitivo_secundario_id uuid         NULL,
    sensibilidade           text         NOT NULL,
    tempo_min_gesto_ms      integer      NOT NULL,
    tempo_max_gesto_ms      integer      NOT NULL,
    urgencia                text         NOT NULL,
    estado                  text         NOT NULL DEFAULT 'drafted',
    calibracao_versao       integer      NOT NULL DEFAULT 0,
    row_version             integer      NOT NULL DEFAULT 0,
    created_at              timestamptz  NOT NULL DEFAULT now(),
    updated_at              timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    FOREIGN KEY (paciente_id) REFERENCES paciente (id) ON DELETE RESTRICT,
    CONSTRAINT ck_comando_rotulo_nonempty     CHECK (length(btrim(rotulo)) > 0),
    CONSTRAINT ck_comando_necessidade_nonempty CHECK (length(btrim(necessidade)) > 0),
    CONSTRAINT ck_comando_sensibilidade       CHECK (sensibilidade IN ('baixa','media','alta')),
    CONSTRAINT ck_comando_urgencia            CHECK (urgencia IN ('emergencia','rotina')),
    CONSTRAINT ck_comando_estado              CHECK (estado IN ('drafted','validated','published','retired')),
    CONSTRAINT ck_comando_timing              CHECK (
        tempo_min_gesto_ms > 0
        AND tempo_min_gesto_ms <= tempo_max_gesto_ms
    ),
    CONSTRAINT ck_comando_distinct_primitives CHECK (
        primitivo_secundario_id IS NULL
        OR primitivo_secundario_id <> primitivo_principal_id
    )
);

CREATE INDEX ix_comando_paciente       ON comando (paciente_id);
CREATE INDEX ix_comando_paciente_estado ON comando (paciente_id, estado);

-- Unicidade: no máx. 1 mapeamento ativo (gesto+necessidade) por paciente.
-- Retired rows são isentos para preservar histórico (ADR-006).
-- primitivo_secundario_id NULL é normalizado para sentinel UUID para permitir unicidade.
CREATE UNIQUE INDEX ux_comando_active_mapping
    ON comando (
        paciente_id,
        primitivo_principal_id,
        coalesce(primitivo_secundario_id, '00000000-0000-0000-0000-000000000000'::uuid),
        necessidade
    )
    WHERE estado <> 'retired';

COMMENT ON TABLE comando IS 'PAC-owned command catalog item. Per-command calibration (ADR-004). History-preserving (ADR-006).';
COMMENT ON COLUMN comando.primitivo_principal_id IS 'Cross-service reference to GEST TipoGesto (not a FK).';
COMMENT ON COLUMN comando.primitivo_secundario_id IS 'Optional secondary primitive — cross-service reference to GEST TipoGesto (not a FK).';
COMMENT ON COLUMN comando.calibracao_versao IS 'Monotonic; +1 on each in-place recalibration of a published command (ADR-004).';
COMMENT ON COLUMN comando.row_version IS 'Monotonic counter bumped by trigger tg_comando_touch on UPDATE. Backs ETag/If-Match.';
COMMENT ON INDEX ux_comando_active_mapping IS 'Prevents duplicate active gesture+need mapping per patient. Retired rows exempt (ADR-006).';
