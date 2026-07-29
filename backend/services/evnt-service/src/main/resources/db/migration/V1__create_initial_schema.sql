-- =============================================================================
-- Migration: V1__create_initial_schema.sql
-- Service:   evnt-service
-- Generated: 2026-07-27 by be-migration-generator.py
-- Source:    contracts/data-model.md (extraído de 4 blocos ```sql```)
--
-- ATENÇÃO: este arquivo é GERADO. Para alterar DDL:
--   1. Edite contracts/data-model.md (blocos ```sql```).
--   2. Re-rode: python scripts/be-migration-generator.py <service> --spec-dir <path> --force
-- =============================================================================

-- schema: global
CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- schema: empresa
CREATE TABLE evento (
    id                       uuid         NOT NULL DEFAULT gen_random_uuid(),
    paciente_id              uuid         NOT NULL,
    comando_ref              varchar(128) NOT NULL,
    command_label_snapshot   varchar(200) NOT NULL,
    urgencia_snapshot        varchar(16)  NOT NULL,
    report_id                uuid         NOT NULL,
    recognized_at            timestamptz  NOT NULL,
    timing_start_ms          bigint       NOT NULL,
    timing_end_ms            bigint       NOT NULL,
    timing_duration_ms       integer      NOT NULL,
    confidence               numeric(4,3) NOT NULL,
    feedback                 varchar(16)  NOT NULL DEFAULT 'nao_avaliado',
    confirmado_por           uuid         NULL,
    confirmado_at            timestamptz  NULL,
    correlation_id           varchar(64)  NULL,
    seq                      bigserial    NOT NULL,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (report_id),
    CHECK (urgencia_snapshot IN ('emergencia','rotina')),
    CHECK (feedback IN ('nao_avaliado','real','falso_positivo')),
    CHECK (timing_duration_ms >= 0 AND timing_end_ms >= timing_start_ms),
    CHECK (confidence >= 0 AND confidence <= 1),
    CHECK ((feedback = 'nao_avaliado' AND confirmado_por IS NULL AND confirmado_at IS NULL)
        OR (feedback IN ('real','falso_positivo') AND confirmado_por IS NOT NULL AND confirmado_at IS NOT NULL))
);

CREATE UNIQUE INDEX ux_evento_report_id ON evento (report_id);
CREATE INDEX ix_evento_paciente_recognized ON evento (paciente_id, recognized_at DESC);
CREATE INDEX ix_evento_open ON evento (recognized_at DESC) WHERE feedback = 'nao_avaliado';
CREATE INDEX ix_evento_falsepositive ON evento (comando_ref) WHERE feedback = 'falso_positivo';
CREATE INDEX ix_evento_seq ON evento (seq);
COMMENT ON TABLE evento IS 'EVNT system-of-record for a recognized help request (ADR-004); tenant-scoped; verdict terminal (ADR-006).';
COMMENT ON COLUMN evento.report_id IS 'Client-generated idempotency key from the edge RecognizedCommandReport (ADR-004).';
COMMENT ON COLUMN evento.seq IS 'Monotonic per-schema sequence powering SSE Last-Event-ID resume (api-contract 3.3).';

-- schema: empresa
CREATE TABLE notification_dispatch (
    id                   uuid         NOT NULL DEFAULT gen_random_uuid(),
    evento_id            uuid         NOT NULL,
    tier                 varchar(20)  NOT NULL,
    target_caregiver_id  uuid         NOT NULL,
    status               varchar(16)  NOT NULL DEFAULT 'queued',
    attempts             integer      NOT NULL DEFAULT 0,
    last_attempt_at      timestamptz  NULL,
    last_error           varchar(300) NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (tier IN ('full_screen_alarm','discreet')),
    CHECK (status IN ('queued','delivered','acknowledged','failed')),
    CHECK (attempts >= 0),
    FOREIGN KEY (evento_id) REFERENCES evento (id) ON DELETE CASCADE
);

CREATE INDEX ix_dispatch_evento ON notification_dispatch (evento_id);
CREATE INDEX ix_dispatch_retry ON notification_dispatch (created_at) WHERE status IN ('queued','failed');
COMMENT ON TABLE notification_dispatch IS 'Per-attempt notification delivery record (ADR-005); retry sweep reads status queued/failed.';

-- schema: empresa
CREATE TABLE report_idempotency (
    report_id      uuid         NOT NULL,
    evento_id      uuid         NOT NULL,
    first_seen_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (report_id),
    FOREIGN KEY (evento_id) REFERENCES evento (id) ON DELETE CASCADE
);

CREATE INDEX ix_report_idempotency_evento ON report_idempotency (evento_id);
COMMENT ON TABLE report_idempotency IS 'One report => one Evento ledger (ADR-004); duplicate reportId returns the original evento_id.';
