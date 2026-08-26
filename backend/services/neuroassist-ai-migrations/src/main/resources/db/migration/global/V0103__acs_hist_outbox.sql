-- ACS migration — schema GLOBAL — faixa V0100..V0199 — owner: acs-service
-- =============================================================================
-- Migration: V0103__acs_hist_outbox.sql
-- Service:   acs-service
-- Schema:    neuroassist_global
-- Issue:     E2E-031 (C-52)
--
-- E2E-019 fez o acs-service produzir `autenticacao.realizada` e `autenticacao.falhou`
-- de verdade. Faltava a durabilidade: o `HistAuditAdapter` é fail-open (uma cuidadora
-- não pode ser impedida de entrar no painel porque o hist-service caiu) mas registrava
-- a própria perda — "NAO gravado e perdido — acs-service nao tem outbox".
--
-- Um fato de auditoria de comando perdido é lacuna no histórico clínico. Um fato de
-- AUTENTICAÇÃO perdido é lacuna em "quem entrou e quando" — o registro que uma auditoria
-- LGPD pede primeiro, e o único que responde "alguém acessou esses dados durante a
-- janela do incidente?".
--
-- ─────────────────────────────────────────────────────────────────────────────
-- POR QUE ESTA TABELA É GLOBAL, ao contrário das do PAC e do EVNT
--
-- Naqueles, `hist_outbox` vive no schema do TENANT e o dreno vincula o tenant antes de
-- abrir a transação — sem isso a consulta cairia no schema global, não acharia a tabela,
-- e o sweeper reportaria sucesso.
--
-- O acs-service não trabalha assim: ele grava fatos de VÁRIAS empresas e o dreno roda sem
-- tenant vinculado. Então o tenant vira COLUNA em vez de ser herdado do schema, e cada
-- entrega usa o token escopado ao tenant DAQUELA LINHA — nunca um qualquer.
-- ─────────────────────────────────────────────────────────────────────────────
--
-- `status` é varchar + CHECK, e NUNCA ENUM nativo: o mapeamento JPA usa String e o
-- Postgres não faz coerção implícita de varchar para ENUM em INSERT. Foi assim que toda
-- gravação no outbox do PAC falhou em produção enquanto os testes (ddl-auto=create-drop)
-- ficavam verdes (C-12/V0207).
-- =============================================================================

CREATE TABLE neuroassist_global.acs_hist_outbox (
    id            uuid        NOT NULL DEFAULT gen_random_uuid(),
    fact_id       uuid        NOT NULL,
    fact_type     text        NOT NULL,
    tenant_id     uuid        NOT NULL,
    payload_json  text        NOT NULL,
    status        varchar(20) NOT NULL DEFAULT 'pending',
    retry_count   integer     NOT NULL DEFAULT 0,
    last_error    text        NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    sent_at       timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_acs_hist_outbox_status CHECK (status IN ('pending', 'sent', 'failed'))
);

-- Um fato entra no outbox no máximo uma vez. Sem isto, uma reentrega que falhasse no meio
-- poderia enfileirar o mesmo fato de novo, e a deduplicação do hist-service esconderia o
-- defeito em vez de o denunciar.
CREATE UNIQUE INDEX ux_acs_hist_outbox_fact_id
    ON neuroassist_global.acs_hist_outbox (fact_id);

CREATE INDEX ix_acs_hist_outbox_pending
    ON neuroassist_global.acs_hist_outbox (created_at)
    WHERE status = 'pending';

COMMENT ON TABLE neuroassist_global.acs_hist_outbox IS
    'ACS outbox de fatos de auditoria para o hist-service (E2E-031). GLOBAL de propósito: o '
    'acs-service grava fatos de várias empresas e o dreno roda sem tenant vinculado.';

COMMENT ON COLUMN neuroassist_global.acs_hist_outbox.tenant_id IS
    'Empresa a que o fato pertence. Coluna, e não schema, porque a tabela é global — e é este '
    'valor que escopa o token de serviço na reentrega.';

COMMENT ON COLUMN neuroassist_global.acs_hist_outbox.fact_id IS
    'Chave de idempotência do hist-service. Cunhada uma vez e reenviada idêntica — é o que '
    'transforma uma reentrega em replay (200) em vez de um segundo fato (201).';

COMMENT ON COLUMN neuroassist_global.acs_hist_outbox.payload_json IS
    'RecordAuditFactRequest serializado no momento do fato. Guardado inteiro, e não re-derivado '
    'depois, para que a reentrega envie exatamente o que a tentativa original enviou.';

COMMENT ON COLUMN neuroassist_global.acs_hist_outbox.last_error IS
    'Motivo da última falha. Estado terminal sem causa registrada é indistinguível de fila '
    'ocupada — foi assim que o C-44 passou despercebido no PAC.';
