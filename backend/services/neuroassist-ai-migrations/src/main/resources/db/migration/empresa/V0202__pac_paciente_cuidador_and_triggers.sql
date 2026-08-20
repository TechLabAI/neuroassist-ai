-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0202__pac_paciente_cuidador_and_triggers.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant, applied by Flyway per-tenant schema run)
-- Based on:  pac-service/contracts/data-model.md §2.2 + §2.5
--
-- Tabela paciente_cuidador (cross-service ref a ACS Cuidador) + trigger de
-- atualização automática de updated_at e row_version nas tabelas mutáveis.
-- Pré-requisito: V0200 (paciente) e V0201 (comando) devem existir.
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: paciente_cuidador
-- Relação many-to-many entre Paciente e Cuidador (ACS).
-- cuidador_responsavel_id é cross-service ref (sem FK cross-service).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE paciente_cuidador (
    paciente_id             uuid        NOT NULL,
    cuidador_responsavel_id uuid        NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (paciente_id, cuidador_responsavel_id),
    FOREIGN KEY (paciente_id) REFERENCES paciente (id) ON DELETE CASCADE
);

CREATE INDEX ix_pac_cuidador_paciente ON paciente_cuidador (paciente_id);

COMMENT ON TABLE paciente_cuidador IS 'Responsible caregivers (ACS Cuidador ids, cross-service refs).';
COMMENT ON COLUMN paciente_cuidador.cuidador_responsavel_id IS 'Cross-service reference to ACS Cuidador (not a FK).';

-- ────────────────────────────────────────────────────────────────────────────
-- Função trigger: pac_touch_row
-- Atualiza updated_at e incrementa row_version em cada UPDATE.
-- Suporta o contrato ETag/If-Match (409 em stale update).
-- ────────────────────────────────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION pac_touch_row() RETURNS trigger AS $$
BEGIN
    NEW.updated_at  := now();
    NEW.row_version := OLD.row_version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger em paciente
CREATE TRIGGER tg_paciente_touch
    BEFORE UPDATE ON paciente
    FOR EACH ROW EXECUTE FUNCTION pac_touch_row();

-- Trigger em comando
CREATE TRIGGER tg_comando_touch
    BEFORE UPDATE ON comando
    FOR EACH ROW EXECUTE FUNCTION pac_touch_row();
