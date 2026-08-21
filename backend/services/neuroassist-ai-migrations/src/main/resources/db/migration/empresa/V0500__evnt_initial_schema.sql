-- EVNT migration — faixa V0500..V0599 — owner: evnt-service
-- =============================================================================
-- Migration: V0500__evnt_initial_schema.sql
-- Service:   evnt-service
-- Schema:    empresa (per-tenant)
-- Pré-requisito: processed_events criado por V0400 (GEST).
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: evento
-- Registro de eventos/alarmes gerados pelo GEST.
-- seq: bigserial para ordenação determinística (idempotência EVNT).
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS evento (
    id                       uuid          NOT NULL,
    paciente_id              varchar(64)   NOT NULL,
    comando_ref              varchar(255)  NULL,
    command_label_snapshot   varchar(255)  NULL,
    urgencia_snapshot        varchar(50)   NOT NULL,
    feedback                 varchar(50)   NOT NULL DEFAULT 'NAO_AVALIADO',
    confirmado_por           varchar(255)  NULL,
    confirmado_at            timestamptz   NULL,
    recognized_at            timestamptz   NULL,
    created_at               timestamptz   NOT NULL DEFAULT now(),
    seq                      bigserial     NOT NULL UNIQUE,
    tenant_id                varchar(100)  NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS ix_evento_paciente_id ON evento (paciente_id);
CREATE INDEX IF NOT EXISTS ix_evento_tenant_id   ON evento (tenant_id);
CREATE INDEX IF NOT EXISTS ix_evento_created_at  ON evento (created_at DESC);

COMMENT ON TABLE evento IS 'EVNT: events/alarms from GEST — empresa-scoped.';

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: report_idempotency
-- Deduplicação de reports JMS por report_id.
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS report_idempotency (
    report_id    varchar(64)  NOT NULL,
    evento_id    uuid         NOT NULL,
    processed_at timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (report_id)
);

-- ────────────────────────────────────────────────────────────────────────────
-- Tabela: notification_dispatch
-- Rastreamento de envio de notificações por tier/cuidador.
-- ────────────────────────────────────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS notification_dispatch (
    id                  uuid         NOT NULL,
    evento_id           uuid         NOT NULL,
    tier                varchar(50)  NOT NULL,
    target_caregiver_id varchar(255) NULL,
    status              varchar(50)  NOT NULL DEFAULT 'QUEUED',
    attempts            integer      NOT NULL DEFAULT 0,
    last_attempt_at     timestamptz  NULL,
    PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS ix_notif_evento_id ON notification_dispatch (evento_id);
CREATE INDEX IF NOT EXISTS ix_notif_status     ON notification_dispatch (status);

COMMENT ON TABLE notification_dispatch IS 'EVNT: outbound notification dispatch tracking.';
