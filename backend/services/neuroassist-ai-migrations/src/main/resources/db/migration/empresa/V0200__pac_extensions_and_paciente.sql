-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0200__pac_extensions_and_paciente.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant, applied by Flyway per-tenant schema run)
-- Based on:  pac-service/contracts/data-model.md §0 + §2.1
--
-- Este arquivo é a contribuição do pac-service ao neuroassist-ai-migrations.
-- Todos os objetos aqui criados são tenant-scoped — aplicados no schema t_<tenantId>.
-- NUNCA hardcode schema — o Flyway aplica este arquivo dentro do schema correto.
--
-- Pré-requisito: uuid-ossp e pgcrypto devem existir no schema global (V0001).
-- Não repetir CREATE EXTENSION aqui — seguir convenção de V0400 (GEST).
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: paciente
-- System of record para o paciente. Tenant data, per-empresa schema.
-- tenant_id redundante (defense-in-depth): garante que bug de schema-routing
-- não atravesse tenants silenciosamente.
-- dispositivo_edge_id e cuidador_responsavel_id: cross-service refs (sem FK).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE paciente (
    id                    uuid         NOT NULL DEFAULT uuid_generate_v4(),
    tenant_id             varchar(255) NOT NULL,
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
COMMENT ON COLUMN paciente.tenant_id IS 'Defense-in-depth: mirrors schema identifier. Row must belong to same tenant as its schema.';
COMMENT ON COLUMN paciente.dispositivo_edge_id IS 'Cross-service reference to EDGE DispositivoEdge (not a FK).';
COMMENT ON COLUMN paciente.row_version IS 'Monotonic counter bumped by trigger tg_paciente_touch on UPDATE. Backs ETag/If-Match.';
