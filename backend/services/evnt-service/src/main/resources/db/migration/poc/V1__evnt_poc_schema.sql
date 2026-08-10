-- =============================================================================
-- Migration PoC: V1__evnt_poc_schema.sql
-- Service:       evnt-service
-- Profile:       poc
-- Purpose:       Schema simplificado para fluxo de PoC (gesture→alerta)
-- =============================================================================

CREATE SCHEMA IF NOT EXISTS neuroassist_poc;
SET search_path = neuroassist_poc;

CREATE TABLE IF NOT EXISTS evento (
    id                      UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    paciente_id             VARCHAR(64) NOT NULL,
    comando_ref             VARCHAR(255),
    command_label_snapshot  VARCHAR(255),
    urgencia_snapshot       VARCHAR(50)  NOT NULL,
    timing_window           JSONB,
    feedback                VARCHAR(50)  NOT NULL DEFAULT 'NAO_AVALIADO',
    confirmado_por          VARCHAR(255),
    confirmado_at           TIMESTAMPTZ,
    recognized_at           TIMESTAMPTZ,
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS notification_dispatch (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id            UUID        NOT NULL REFERENCES evento(id),
    tier                 VARCHAR(50) NOT NULL,
    target_caregiver_id  VARCHAR(255),
    status               VARCHAR(50) NOT NULL DEFAULT 'QUEUED',
    attempts             INTEGER     NOT NULL DEFAULT 0,
    last_attempt_at      TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS report_idempotency (
    report_id    VARCHAR(64) PRIMARY KEY,
    evento_id    UUID        NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Tabela de idempotência de eventos JMS (ProcessedEvent entity)
CREATE TABLE IF NOT EXISTS processed_events (
    event_id     UUID         PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
