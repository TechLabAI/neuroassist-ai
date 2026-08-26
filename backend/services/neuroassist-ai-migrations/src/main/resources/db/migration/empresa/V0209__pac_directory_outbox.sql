-- PAC migration — empresa schema — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0209__pac_directory_outbox.sql
-- Service:   pac-service
-- Schema:    <empresa> (por tenant)
-- Issue:     E2E-015 — C-30/C-42/C-43
--
-- O vínculo cuidador↔paciente vivia em duas tabelas sem nada entre elas:
-- o PAC grava `paciente_cuidador` (schema do tenant) e o ACS lê
-- `caregiver_patient_link` (schema global). Resultado: todo paciente criado
-- pelo PAC nascia com o directory do ACS vazio — e um alarme para esse
-- paciente não tinha destinatário. O Flow-03 só passou porque o roteiro
-- chamava `POST /acs/caregivers/{c}/patients/{p}/link` à mão.
--
-- Esta tabela é a metade durável da propagação. Ela é gravada na MESMA
-- transação que `paciente_cuidador`, então nunca existe um estado em que o
-- vínculo local exista e a intenção de propagá-lo não.
--
-- `status` é varchar + CHECK, e não ENUM nativo, de propósito: o mapeamento
-- JPA usa String e o Postgres não faz coerção implícita de varchar para ENUM
-- em INSERT. Foi assim que toda gravação em `hist_outbox` falhou em produção
-- enquanto os testes (ddl-auto=create-drop) ficavam verdes — C-12/V0207.
-- =============================================================================

CREATE TABLE directory_outbox (
    id           uuid        NOT NULL DEFAULT uuid_generate_v4(),
    operation    varchar(10) NOT NULL,
    paciente_id  uuid        NOT NULL,
    cuidador_id  uuid        NOT NULL,
    status       varchar(20) NOT NULL DEFAULT 'pending',
    retry_count  integer     NOT NULL DEFAULT 0,
    last_error   text        NULL,
    created_at   timestamptz NOT NULL DEFAULT now(),
    sent_at      timestamptz NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_directory_outbox_operation CHECK (operation IN ('LINK', 'UNLINK')),
    CONSTRAINT ck_directory_outbox_status    CHECK (status IN ('pending', 'sent', 'failed'))
);

-- O dreno lê o mais antigo primeiro e para no primeiro erro do lote. Isso não é
-- só cortesia com o ACS: preserva a ORDEM entre um LINK e o UNLINK seguinte do
-- mesmo par. Entregues fora de ordem, o vínculo revogado voltaria a existir.
CREATE INDEX ix_directory_outbox_pending ON directory_outbox (created_at)
    WHERE status = 'pending';

COMMENT ON TABLE directory_outbox IS
    'Propagação do vínculo cuidador-paciente do PAC para o directory do ACS (E2E-015). '
    'Gravada na mesma transação que paciente_cuidador; drenada por DirectoryOutboxScheduler.';

COMMENT ON COLUMN directory_outbox.operation IS
    'LINK → POST /api/v1/acs/caregivers/{c}/patients/{p}/link; UNLINK → DELETE no mesmo path.';

COMMENT ON COLUMN directory_outbox.status IS
    'pending → aguardando entrega; sent → aceito pelo ACS; failed → desistiu (retries esgotados '
    'ou recusa terminal do ACS, ex.: 409 de último vínculo ativo).';

COMMENT ON COLUMN directory_outbox.last_error IS
    'Último motivo de falha. Existe para que uma entrada `failed` diga POR QUE desistiu — '
    'um estado terminal sem causa registrada é indistinguível de fila ocupada.';

-- ────────────────────────────────────────────────────────────────────────────
-- Backfill: todo vínculo que já existe no PAC nunca chegou ao ACS.
-- Sem isto a correção só valeria para pacientes criados a partir de agora, e os
-- pacientes existentes continuariam com alarme sem destinatário.
--
-- Pacientes com erasure LGPD ficam de fora: o dado pessoal foi destruído e não
-- há motivo para criar roteamento de alarme para quem não gera mais alarme.
-- O ACS trata LINK repetido como idempotente (200), então rodar isto contra um
-- directory já populado não duplica nada.
-- ────────────────────────────────────────────────────────────────────────────
INSERT INTO directory_outbox (operation, paciente_id, cuidador_id)
SELECT 'LINK', pc.paciente_id, pc.cuidador_responsavel_id
  FROM paciente_cuidador pc
  JOIN paciente p ON p.id = pc.paciente_id
 WHERE p.erased_at IS NULL;
