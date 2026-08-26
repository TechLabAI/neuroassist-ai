-- GEST migration — faixa V0400..V0499 — owner: gest-service
-- =============================================================================
-- Migration: V0401__gest_detection_thresholds.sql
-- Service:   gest-service
-- Schema:    empresa (per-tenant)
-- Issue:     E2E-033 (C-54)
--
-- O perfil publicado passa a carregar limiares de detecção; cada primitivo passa a carregar a
-- faixa de sensibilidade com que detectá-lo.
--
-- Até aqui `classification_params` era um jsonb livre cujo comentário prometia quatro chaves
-- (confidenceThreshold, minWindowMs, maxWindowMs, compositeMaxGapMs) que NENHUMA linha de código
-- jamais escreveu ou leu. Os limiares EAR/BRI viviam como constante no agente de borda, iguais
-- para todo paciente — enquanto o PAC guarda `comando.sensibilidade` por paciente desde a V0201.
--
-- A coluna continua sendo a mesma; o que muda é que agora ela tem forma, e a forma é validada
-- no domínio (DetectionThresholdTable).
-- =============================================================================

-- ────────────────────────────────────────────────────────────────────────────
-- 1. Faixa de sensibilidade por primitivo
-- ────────────────────────────────────────────────────────────────────────────
-- Mesmo vocabulário de `comando.sensibilidade` (V0201), literalmente: quando a rota por paciente
-- for ligada, é para ser correspondência de nome e não tradução.
--
-- DEFAULT 'media' cobre as linhas existentes: 'media' é, valor por valor, o que os agentes já
-- usavam como constante. Nenhum perfil publicado antes desta migration muda de comportamento.
ALTER TABLE detection_profile_primitive
    ADD COLUMN sensibilidade text NOT NULL DEFAULT 'media';

ALTER TABLE detection_profile_primitive
    ADD CONSTRAINT ck_profile_primitive_sensibilidade
        CHECK (sensibilidade IN ('baixa', 'media', 'alta'));

COMMENT ON COLUMN detection_profile_primitive.sensibilidade IS
    'baixa|media|alta — qual faixa de detection_profile.classification_params aplicar a este gesto.';

-- ────────────────────────────────────────────────────────────────────────────
-- 2. Backfill dos perfis existentes
-- ────────────────────────────────────────────────────────────────────────────
-- Toda linha antiga carrega um objeto que o novo formato não sabe ler (na prática, `{}` — nunca
-- houve escritor). O adapter falha alto ao encontrar um, de propósito: servir limiar inventado à
-- borda é pior que falhar. Então o backfill não é opcional.
--
-- Os números são os defaults de DetectionThresholdTable.defaults(), e `media` espelha
-- backend/edge_agent_core.py. Os três lados são vigiados por
-- backend/scripts/check_edge_agent_drift.py.
UPDATE detection_profile
SET classification_params = jsonb_build_object(
        'baixa', jsonb_build_object(
            'earClose', 0.19, 'earOpen', 0.24, 'earOtherMax', 0.27,
            'blinkMinFrames', 3, 'doubleBlinkWindowMs', 1500,
            'briRaiseOn', 0.34, 'briRaiseOff', 0.26, 'briOtherMax', 0.29,
            'browMinFrames', 7),
        'media', jsonb_build_object(
            'earClose', 0.22, 'earOpen', 0.27, 'earOtherMax', 0.30,
            'blinkMinFrames', 2, 'doubleBlinkWindowMs', 2000,
            'briRaiseOn', 0.30, 'briRaiseOff', 0.22, 'briOtherMax', 0.25,
            'browMinFrames', 5),
        'alta', jsonb_build_object(
            'earClose', 0.25, 'earOpen', 0.30, 'earOtherMax', 0.33,
            'blinkMinFrames', 2, 'doubleBlinkWindowMs', 2500,
            'briRaiseOn', 0.26, 'briRaiseOff', 0.18, 'briOtherMax', 0.21,
            'browMinFrames', 3))
WHERE NOT (classification_params ? 'media');

COMMENT ON COLUMN detection_profile.classification_params IS
    'jsonb: DetectionThresholdTable — as tres faixas (baixa|media|alta), cada uma com o conjunto '
    'completo de limiares EAR/BRI. Formato validado no dominio; ver E2E-033.';

-- ────────────────────────────────────────────────────────────────────────────
-- 3. Guarda de forma
-- ────────────────────────────────────────────────────────────────────────────
-- O CHECK anterior (ck_profile_params_object) só exigia "é um objeto" — foi o que permitiu que a
-- coluna passasse a existência inteira vazia sem ninguém notar. Exigir as três faixas faz a
-- promessa da coluna ser verificável pelo banco, e não só pelo comentário.
ALTER TABLE detection_profile
    ADD CONSTRAINT ck_profile_params_three_bands
        CHECK (
            classification_params ? 'baixa'
            AND classification_params ? 'media'
            AND classification_params ? 'alta'
        );
