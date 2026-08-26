-- EVNT migration — faixa V0500..V0599 — owner: evnt-service
-- =============================================================================
-- Migration: V0501__evnt_hist_outbox.sql
-- Service:   evnt-service
-- Schema:    empresa (per-tenant)
-- Issue:     E2E-017
--
-- O `AuditTrailFeignAdapter` é fail-open, o que está certo: criar um alarme não
-- pode falhar porque o hist-service caiu (AUD-10). O que faltava era durabilidade.
-- O adapter registrava literalmente a perda:
--
--   "Audit fact {} ({}) was NOT recorded and is lost — evnt-service has no outbox"
--
-- Um `evento.criado` ou `evento.confirmado` produzido durante uma indisponibilidade
-- do HIST estava perdido para sempre — e são os fatos de um ALARME, o registro que
-- mais importa numa auditoria clínica.
--
-- NOME DA TABELA: `evnt_hist_outbox`, e não `hist_outbox`.
--
-- O PAC já tem uma `hist_outbox` NESTE MESMO schema (V0205), com outra forma — sem
-- `fact_id`, sem `last_error`. A convenção de faixas de V separa os NÚMEROS por módulo,
-- mas os serviços compartilham o schema do tenant: o espaço de NOMES é único.
--
-- Escrito primeiro como `CREATE TABLE IF NOT EXISTS hist_outbox`, isto teria feito
-- silenciosamente nada, e o evnt-service passaria a mapear a tabela do PAC — que não tem
-- as colunas que ele grava. Daí também o `CREATE TABLE` sem `IF NOT EXISTS` abaixo: uma
-- colisão futura tem de falhar alto, não se acomodar na tabela alheia.
--
-- Mesma forma do `hist_outbox` do PAC (V0205/V0207), inclusive as lições:
--
--  * `status` é varchar + CHECK, e NUNCA ENUM nativo. O mapeamento JPA usa String e
--    o Postgres não faz coerção implícita de varchar para ENUM em INSERT — foi assim
--    que toda gravação no outbox do PAC falhou em produção enquanto os testes
--    (ddl-auto=create-drop) ficavam verdes (C-12).
--  * `fact_id` é cunhado UMA vez e reenviado idêntico: é o que faz o hist-service
--    deduplicar em vez de acrescentar o fato duas vezes (C-5, HIST ADR-005).
-- =============================================================================

CREATE TABLE evnt_hist_outbox (
    id            uuid        NOT NULL DEFAULT uuid_generate_v4(),
    fact_id       uuid        NOT NULL,
    fact_type     text        NOT NULL,
    payload_json  text        NOT NULL,
    status        varchar(20) NOT NULL DEFAULT 'pending',
    retry_count   integer     NOT NULL DEFAULT 0,
    last_error    text        NULL,
    created_at    timestamptz NOT NULL DEFAULT now(),
    sent_at       timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_evnt_hist_outbox_status CHECK (status IN ('pending', 'sent', 'failed'))
);

-- Um fato entra no outbox no máximo uma vez. Sem isto, uma reentrega que falhasse no
-- meio poderia enfileirar o mesmo fato de novo, e a deduplicação do hist-service
-- esconderia o defeito em vez de o denunciar.
CREATE UNIQUE INDEX ux_evnt_hist_outbox_fact_id ON evnt_hist_outbox (fact_id);

CREATE INDEX ix_evnt_hist_outbox_pending ON evnt_hist_outbox (created_at)
    WHERE status = 'pending';

COMMENT ON TABLE evnt_hist_outbox IS
    'EVNT outbox de fatos de auditoria para o hist-service (E2E-017). Gravado na MESMA '
    'transação do evento, drenado por HistOutboxScheduler.';

COMMENT ON COLUMN evnt_hist_outbox.fact_id IS
    'Chave de idempotência do hist-service. Cunhada uma vez e reenviada idêntica — é o que '
    'transforma uma reentrega em replay (200) em vez de um segundo fato (201).';

COMMENT ON COLUMN evnt_hist_outbox.payload_json IS
    'RecordAuditFactRequest serializado no momento em que o fato aconteceu. Guardado inteiro, '
    'e não re-derivado depois, para que a reentrega envie exatamente o que a tentativa '
    'original enviou.';

COMMENT ON COLUMN evnt_hist_outbox.status IS
    'pending → aguardando entrega; sent → aceito pelo HIST; failed → desistiu após max-retries.';

COMMENT ON COLUMN evnt_hist_outbox.last_error IS
    'Motivo da última falha. Um estado terminal sem causa registrada é indistinguível de fila '
    'ocupada — foi assim que o C-44 passou despercebido no PAC.';
