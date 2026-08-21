-- PAC migration — empresa schema — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0207__pac_hist_outbox_status_varchar.sql
-- Service:   pac-service
-- Schema:    <empresa>
--
-- Converte hist_outbox.status de ENUM nativo para varchar + CHECK.
--
-- MOTIVO (E2E-004 / C-12): HistOutboxJpaEntity mapeia status como String, e o
-- driver JDBC envia o parâmetro como varchar. O Postgres não faz coerção
-- implícita de varchar para um tipo ENUM em INSERT, então toda gravação no
-- outbox falhava com:
--
--   ERROR: column "status" is of type hist_outbox_status
--          but expression is of type character varying
--
-- O defeito era invisível nos testes porque a suíte do PAC roda com
-- ddl-auto=create-drop — o Hibernate cria a coluna como varchar e o ENUM do
-- Flyway nunca entra em cena. Alinhar o schema ao mapeamento (e não o
-- contrário) mantém as duas configurações idênticas e evita que a divergência
-- volte a se esconder.
--
-- O CHECK preserva a mesma restrição de domínio do ENUM.
-- =============================================================================

-- O índice parcial referencia status no predicado; precisa sair antes da troca de tipo,
-- senão o ALTER falha com "operator does not exist: character varying = hist_outbox_status".
DROP INDEX IF EXISTS ix_hist_outbox_pending;

ALTER TABLE hist_outbox
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE hist_outbox
    ALTER COLUMN status TYPE varchar(20) USING status::text;

ALTER TABLE hist_outbox
    ALTER COLUMN status SET DEFAULT 'pending';

ALTER TABLE hist_outbox
    ADD CONSTRAINT ck_hist_outbox_status
        CHECK (status IN ('pending', 'sent', 'failed'));

CREATE INDEX ix_hist_outbox_pending ON hist_outbox (created_at)
    WHERE status = 'pending';

DROP TYPE IF EXISTS hist_outbox_status;

COMMENT ON COLUMN hist_outbox.status IS
    'Estado de entrega do fato: pending | sent | failed. varchar + CHECK (não ENUM) '
    'para casar com o mapeamento String de HistOutboxJpaEntity.';
