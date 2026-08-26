-- EVNT migration — faixa V0500..V0599 — owner: evnt-service
-- =============================================================================
-- Migration: V0503__evnt_referential_and_value_constraints.sql
-- Service:   evnt-service
-- Schema:    empresa (per-tenant)
-- Issue:     EVNT-011 (residual) — reconciliação 2026-08-26
-- =============================================================================
--
-- POR QUÊ
-- -------
-- V0500 criou `report_idempotency` e `notification_dispatch` referenciando `evento.id` só por
-- convenção de aplicação — sem FK, sem CHECK. Verificado nesta sessão que os dados de dev (5
-- schemas de tenant) já respeitam todas as regras abaixo, então a migration só declara em DDL uma
-- integridade que o código já mantinha por conta própria. Adiada até agora por não haver dados de
-- produção que pudessem conflitar; feita agora antes que existam.
--
-- Os valores dos CHECK vêm dos enums/constantes reais do código (não de suposição):
--   Tier.java: full_screen_alarm, discreet
--   DispatchStatuses.java: QUEUED, DELIVERED, NO_SESSION, ACKNOWLEDGED, ESCALATED, FAILED
--   Feedback.java: NAO_AVALIADO, REAL, FALSO_POSITIVO
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- evento — feedback só pode ser um dos três valores, e confirmado_por/confirmado_at
-- andam juntos: ambos nulos enquanto NAO_AVALIADO, ambos preenchidos assim que não é mais
-- (ver EventConfirmationService — os três campos são sempre escritos na mesma transação).
-- ────────────────────────────────────────────────────────────────────────────
ALTER TABLE evento
    ADD CONSTRAINT ck_evento_feedback
        CHECK (feedback IN ('NAO_AVALIADO', 'REAL', 'FALSO_POSITIVO'));

ALTER TABLE evento
    ADD CONSTRAINT ck_evento_confirmacao_conjunta
        CHECK (
            (feedback = 'NAO_AVALIADO' AND confirmado_por IS NULL AND confirmado_at IS NULL)
            OR
            (feedback <> 'NAO_AVALIADO' AND confirmado_por IS NOT NULL AND confirmado_at IS NOT NULL)
        );

-- ────────────────────────────────────────────────────────────────────────────
-- report_idempotency — cada linha deduplica um report contra um evento que precisa existir.
-- ON DELETE CASCADE: apagar o evento (nunca feito hoje pela aplicação, mas correto por definição)
-- não deve deixar registro de idempotência órfão apontando para nada.
-- ────────────────────────────────────────────────────────────────────────────
ALTER TABLE report_idempotency
    ADD CONSTRAINT fk_report_idempotency_evento
        FOREIGN KEY (evento_id) REFERENCES evento (id) ON DELETE CASCADE;

-- ────────────────────────────────────────────────────────────────────────────
-- notification_dispatch — mesma razão da FK acima, mais os CHECK de valor que V0500 deixou
-- apenas como convenção de código.
-- ────────────────────────────────────────────────────────────────────────────
ALTER TABLE notification_dispatch
    ADD CONSTRAINT fk_notification_dispatch_evento
        FOREIGN KEY (evento_id) REFERENCES evento (id) ON DELETE CASCADE;

ALTER TABLE notification_dispatch
    ADD CONSTRAINT ck_notification_dispatch_tier
        CHECK (tier IN ('full_screen_alarm', 'discreet'));

ALTER TABLE notification_dispatch
    ADD CONSTRAINT ck_notification_dispatch_status
        CHECK (status IN ('QUEUED', 'DELIVERED', 'NO_SESSION', 'ACKNOWLEDGED', 'ESCALATED', 'FAILED'));
