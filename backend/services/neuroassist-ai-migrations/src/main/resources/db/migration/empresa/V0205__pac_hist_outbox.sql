-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0205__pac_hist_outbox.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant, applied by Flyway per-tenant schema run)
-- Issue:     INT-PAC-004 — HistFactClient + outbox (P6)
--
-- Tabela de outbox para domain facts direcionados ao hist-service.
-- Garante durabilidade: se HIST estiver indisponível no momento do evento,
-- o fato permanece aqui para retry posterior.
-- Os fatos cobertos são: comando.published, comando.recalibrated, comando.retired.
-- =============================================================================

CREATE TYPE hist_outbox_status AS ENUM ('pending', 'sent', 'failed');

CREATE TABLE hist_outbox (
    id             uuid                NOT NULL DEFAULT uuid_generate_v4(),
    event_type     text                NOT NULL,
    payload_json   text                NOT NULL,
    status         hist_outbox_status  NOT NULL DEFAULT 'pending',
    retry_count    integer             NOT NULL DEFAULT 0,
    created_at     timestamptz         NOT NULL DEFAULT now(),
    sent_at        timestamptz         NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_hist_outbox_pending ON hist_outbox (created_at)
    WHERE status = 'pending';

COMMENT ON TABLE hist_outbox IS
    'PAC outbox for domain facts targeting hist-service. '
    'Ensures durability when HIST is unavailable. '
    'Populated within the same transaction as the lifecycle change.';

COMMENT ON COLUMN hist_outbox.event_type IS
    'Domain event type: comando.published | comando.recalibrated | comando.retired';

COMMENT ON COLUMN hist_outbox.payload_json IS
    'JSON-serialized HistFactRequest payload.';

COMMENT ON COLUMN hist_outbox.status IS
    'pending → awaiting delivery; sent → delivered successfully; failed → max retries exceeded';
