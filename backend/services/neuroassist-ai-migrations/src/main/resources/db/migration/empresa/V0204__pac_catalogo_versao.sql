-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0204__pac_catalogo_versao.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant, applied by Flyway per-tenant schema run)
-- Issue:     PAC-019 — PacienteVersionBumper (P6)
--
-- Adiciona coluna catalogo_versao à tabela paciente.
-- Incrementado atomicamente sempre que um Comando publicado é criado,
-- recalibrado ou aposentado — serve como ETag para GET .../catalogo-ativo.
-- =============================================================================

ALTER TABLE paciente
    ADD COLUMN catalogo_versao INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN paciente.catalogo_versao IS
    'Monotonic counter bumped atomically when a published Comando changes '
    '(publish, recalibrate, retire). Serves as ETag for GET .../catalogo-ativo.';
