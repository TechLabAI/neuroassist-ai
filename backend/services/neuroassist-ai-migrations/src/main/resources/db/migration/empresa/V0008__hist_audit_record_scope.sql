-- HIST migration — faixa V0001..V0099 — owner: hist-service
-- =============================================================================
-- Migration: V0008__hist_audit_record_scope.sql
-- Service:   hist-service
-- Schema:    empresa (per-tenant)
-- Motivo:    C-16 — o escopo de cuidadora era aplicado em memória, DEPOIS da
--            paginação, comparando subject_id com ids de paciente.
-- =============================================================================
--
-- Três defeitos numa só linha de código (HistoryQueryService):
--
--   1. Vazamento de contagem — `total` vinha da consulta sem o corte de escopo.
--   2. Paginação incorreta — o corte acontecia depois do LIMIT/OFFSET, então uma
--      cuidadora podia receber página 0 vazia tendo fatos na página 3.
--   3. Comparação de subject errada — fatos de comando têm subject_type='comando',
--      então NENHUM fato de comando passava no filtro, nem para a cuidadora
--      responsável pelo paciente daquele comando.
--
-- A correção exige o escopo no SQL, e o SQL precisa de uma coluna que ligue o
-- fato ao paciente. O payload tem `pacienteId` nos fatos de comando, mas é
-- cifrado em repouso (crypto-shredding LGPD, V0006) — ilegível para o WHERE.
-- Daí a coluna denormalizada, preenchida pelo produtor no momento da ingestão.
--
-- IMPORTANTE: esta coluna NÃO entra na canonicalização do hash
-- (Sha256HashChainAdapter.canonicalize monta um TreeMap com campos nomeados).
-- Se entrasse, todo registro já gravado deixaria de verificar.

ALTER TABLE audit_record
    ADD COLUMN IF NOT EXISTS scope_paciente_id uuid NULL;

COMMENT ON COLUMN audit_record.scope_paciente_id IS
    'Paciente a que este fato se refere, para filtro de escopo de cuidadora. '
    'Denormalizado porque o payload é cifrado e o subject de um fato de comando '
    'é o comando, não o paciente. NULL = fato sem escopo de paciente (visível '
    'apenas com hist:read:all).';

-- Índice composto na ordem em que o WHERE é montado: tenant sempre presente,
-- escopo quando o chamador é cuidadora, ordenação por recorded_at.
CREATE INDEX IF NOT EXISTS ix_audit_record_scope
    ON audit_record (tenant_id, scope_paciente_id, recorded_at DESC);

-- ────────────────────────────────────────────────────────────────────────────
-- Backfill do que é derivável sem ler o payload.
--
-- Fatos cujo subject JÁ é o paciente (evento.criado, evento.confirmado,
-- acesso.*, dispositivo.*) têm o escopo no próprio subject_id.
--
-- Fatos de comando gravados ANTES desta migration ficam com scope NULL: o
-- pacienteId deles só existe dentro do payload cifrado. Consequência assumida —
-- eles continuam invisíveis para a cuidadora, exatamente como eram antes desta
-- migration; os novos passam a ser visíveis. Reprocessar exigiria decifrar linha
-- a linha com a chave de cada paciente, o que contraria o desenho do
-- crypto-shredding.
-- ────────────────────────────────────────────────────────────────────────────
UPDATE audit_record
   SET scope_paciente_id = subject_id
 WHERE subject_type = 'paciente'
   AND scope_paciente_id IS NULL;
