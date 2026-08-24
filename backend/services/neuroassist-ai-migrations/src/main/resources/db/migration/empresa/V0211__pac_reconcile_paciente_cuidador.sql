-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0211__pac_reconcile_paciente_cuidador.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant)
-- Issue:     E2E-028 (C-47), fechamento da segunda porta do vínculo
--
-- Reconcilia a projeção local `paciente_cuidador` com a autoridade, que é o
-- `neuroassist_global.caregiver_patient_link` do ACS.
--
-- POR QUE ISTO É NECESSÁRIO
--
-- Existiam DUAS portas para criar um vínculo cuidadora-paciente:
--
--   1. pelo PAC  — grava aqui e propaga ao ACS  (as duas pontas ficam sabendo)
--   2. direto no ACS — a coordenadora tinha `acs.vinculo.criar`  (o PAC nunca soube)
--
-- A segunda porta acabou de ser fechada: nenhum papel humano tem mais essa authority;
-- ela sobrevive apenas no cliente de serviço `pac-service`, que a usa para propagar.
-- Mas fechar a porta não desfaz o que passou por ela.
--
-- No ambiente de desenvolvimento a divergência era de 17 pacientes com vínculo ATIVO
-- no ACS e nenhuma linha aqui — e ZERO no sentido inverso. A direção única confirma
-- o diagnóstico: o vazamento era pela porta 2, e a reparação é num sentido só.
--
-- POR QUE O ACS É A FONTE E NÃO O PAC
--
-- ADR-010: cobertura é fato de acesso, e quem responde "quem pode ver este paciente?"
-- é o ACS. A tabela local é uma projeção que o PAC mantém para responder rápido —
-- projeção não vira fonte. Onde os dois discordam, o ACS está certo por definição.
--
-- O QUE ESTA MIGRATION NÃO FAZ
--
-- Não apaga nada. Se existisse uma linha aqui sem link ativo no ACS, ela permaneceria
-- — apagar exigiria certeza de que o ACS nunca perdeu um link, e essa certeza não
-- existe. No dev esse caso é zero; se aparecer em outro ambiente, é investigação, não
-- limpeza automática.
-- =============================================================================

INSERT INTO paciente_cuidador (paciente_id, cuidador_responsavel_id, created_at)
SELECT link.paciente_id,
       link.cuidador_id,
       -- Preserva quando o vínculo nasceu de verdade, no ACS. Usar now() aqui faria
       -- todo vínculo antigo parecer criado no dia da migration.
       link.criado_em
FROM neuroassist_global.caregiver_patient_link link
-- O tenant do link tem de ser o dono DESTE schema. Sem esta junção, uma reconciliação
-- rodando no schema da empresa A puxaria vínculos da empresa B — que é precisamente o
-- vazamento entre empresas que o sistema inteiro existe para impedir.
JOIN neuroassist_global.tenant t
  ON t.tenant_id = link.tenant_id
 AND t.schema_nome = current_schema()
-- Só pacientes que existem aqui e não foram apagados (LGPD).
JOIN paciente p
  ON p.id = link.paciente_id
 AND p.erased_at IS NULL
WHERE link.status = 'ACTIVE'
  AND NOT EXISTS (
        SELECT 1
        FROM paciente_cuidador pc
        WHERE pc.paciente_id = link.paciente_id
          AND pc.cuidador_responsavel_id = link.cuidador_id
      );

-- Nota sobre reexecução: a migration é idempotente pelo `NOT EXISTS`, mas o Flyway não
-- a roda duas vezes de qualquer forma. O `NOT EXISTS` existe para o caso de o schema
-- já ter sido parcialmente reconciliado à mão antes desta versão.
--
-- RESULTADO NO DEV, E O QUE SOBROU DE PROPÓSITO
--
--   antes:  36 pacientes ativos, 11 com vínculo local, 25 "descobertos"
--   depois: 46 vínculos, 15 "descobertos"
--   divergência ACS→PAC: 17 → 7    (PAC→ACS: 0, antes e depois)
--
-- Os 7 restantes são pacientes **apagados por LGPD** aqui (`erased_at` preenchido) cujo
-- link continua ACTIVE no ACS. A cláusula `p.erased_at IS NULL` acima os exclui de
-- propósito: recriar o vínculo local de um paciente apagado seria reconstruir associação
-- de um dado que se pediu para remover.
--
-- Isso expõe uma pergunta que não é desta migration resolver: **o apagamento LGPD no PAC
-- não revoga o vínculo no ACS.** Manter o `paciente_cuidador` local é decisão registrada
-- (é o registro de que houve cuidado, não dado pessoal); mas o link do ACS é *acesso*, e
-- acesso a um paciente apagado não tem a mesma justificativa. Precisa de decisão — está
-- sinalizado no ES.
