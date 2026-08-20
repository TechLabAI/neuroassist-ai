-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0203__pac_tipo_gesto_ref.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant, applied by Flyway per-tenant schema run)
-- Based on:  pac-service/contracts/data-model.md §2.4
--
-- Tabela tipo_gesto_ref: read-model cache opcional de TipoGesto (GEST-owned).
-- NAO autoritativa para emergency gate — gate consulta GEST ao vivo (ADR-005).
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: tipo_gesto_ref
-- Projeção read-only de TipoGesto (GEST). Cache derivado, nunca autoritativo.
-- 'supported' aqui é advisory para UI; o gate lê GEST ao vivo (ADR-005).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE tipo_gesto_ref (
    tipo_gesto_id  uuid         NOT NULL,
    nome           text         NOT NULL,
    supported      boolean      NOT NULL DEFAULT false,
    synced_at      timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (tipo_gesto_id)
);

COMMENT ON TABLE tipo_gesto_ref IS 'Read-only projection of GEST TipoGesto (display cache). NOT authoritative for the emergency gate (ADR-005).';
COMMENT ON COLUMN tipo_gesto_ref.supported IS 'Advisory only — emergency gate always queries GEST live (ADR-005).';
COMMENT ON COLUMN tipo_gesto_ref.synced_at IS 'Timestamp of last sync from GEST.';
