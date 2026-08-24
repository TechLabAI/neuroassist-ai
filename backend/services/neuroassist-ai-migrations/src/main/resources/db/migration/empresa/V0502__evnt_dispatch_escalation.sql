-- EVNT migration — faixa V0500..V0599 — owner: evnt-service
-- =============================================================================
-- Migration: V0502__evnt_dispatch_escalation.sql
-- Service:   evnt-service
-- Schema:    empresa (per-tenant)
-- Issue:     E2E-020 · ADR-011 (o que acontece com um alarme que não chega)
-- =============================================================================
--
-- POR QUÊ
-- -------
-- Até aqui um dispatch que esgotava `evnt.dispatch-retry.max-attempts` virava FAILED e
-- morria em silêncio: nenhuma outra cuidadora era acionada, e o motivo da saída da fila
-- não ficava registrado em lugar nenhum. O ADR-011 decidiu três coisas, e as três
-- precisam de estado persistido:
--
--   1. ESCALONAR — o alarme sobe uma escada de QUATRO graus, um por vez: a cuidadora
--      destinatária original, as demais cuidadoras do paciente, a coordenadora da
--      clínica e, em último grau, o apoio do paciente. Para responder "por que a
--      coordenadora foi acionada?" depois de um incidente é preciso saber qual degrau
--      cada linha ocupou (`escalation_order`), POR QUE aquela pessoa entrou
--      (`escalation_grade`) e em qual passada da escada a linha foi criada
--      (`escalation_round`). O índice sozinho não responde a segunda pergunta.
--   2. NUNCA DESISTIR — esgotada a escada, o alarme reinicia no degrau 0. Sem um
--      instante de "só tente de novo depois disto" (`next_attempt_at`) esse ciclo vira
--      reentrega em loop apertado e um alarme antigo monopoliza o batch do sweep.
--   3. EXPIRAR POR RELEVÂNCIA CLÍNICA — e ajuda nunca expira. Quando um alarme
--      efetivamente sai da fila, `outcome_reason` diz por quê, em texto legível por
--      quem investiga o incidente.
--
-- Todas as colunas têm DEFAULT: as linhas já existentes continuam válidas e são lidas
-- como "degrau 0, rodada 0, sem espera, sem desfecho declarado".
-- =============================================================================

ALTER TABLE notification_dispatch
    ADD COLUMN IF NOT EXISTS escalation_order integer     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS escalation_grade varchar(50) NULL,
    ADD COLUMN IF NOT EXISTS escalation_round integer     NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS next_attempt_at  timestamptz NULL,
    ADD COLUMN IF NOT EXISTS outcome_reason   varchar(500) NULL;

COMMENT ON COLUMN notification_dispatch.escalation_order IS
    'EVNT/ADR-011: degrau da escada de cuidadoras (0 = destinatária original).';
COMMENT ON COLUMN notification_dispatch.escalation_grade IS
    'EVNT/ADR-011: grau normativo da escada — CUIDADORA_ORIGINAL, CUIDADORA_ADICIONAL, COORDENADOR_CLINICA, PACIENTE_APOIO (ou DESCONHECIDO para um papel novo do ACS).';
COMMENT ON COLUMN notification_dispatch.escalation_round IS
    'EVNT/ADR-011: quantas passadas completas da escada já ocorreram para este alarme.';
COMMENT ON COLUMN notification_dispatch.next_attempt_at IS
    'EVNT/ADR-011: backoff entre rodadas — NULL significa elegível já no próximo sweep.';
COMMENT ON COLUMN notification_dispatch.outcome_reason IS
    'EVNT/ADR-011: por que esta linha saiu da fila (escalonada ou expirada). Nunca em branco quando o status é terminal por decisão do sweep.';

-- O sweep varre por (status ∈ retentáveis) AND (next_attempt_at IS NULL OR <= now),
-- ordenando por rodada antes de data para que um alarme antigo não passe à frente de um
-- recém-chegado. Este índice cobre o filtro e a primeira chave de ordenação.
CREATE INDEX IF NOT EXISTS ix_notif_sweep
    ON notification_dispatch (status, escalation_round, next_attempt_at, last_attempt_at);
