-- =============================================================================
-- Migration: V2__gest_poc_processed_events.sql
-- Service:   gest-service
-- Profile:   poc
-- Motivo:    Tabela de idempotência JMS — gerada no scaffold mas ausente em V1.
-- =============================================================================

SET search_path = neuroassist_poc;

CREATE TABLE processed_events (
    event_id     UUID PRIMARY KEY,
    event_type   VARCHAR(100) NOT NULL,
    processed_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
