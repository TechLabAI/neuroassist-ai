-- PAC migration — faixa V0200..V0299 — owner: pac-service
-- =============================================================================
-- Migration: V0212__pac_revoke_links_of_erased_patients.sql
-- Service:   pac-service
-- Schema:    empresa (per-tenant)
-- Contexto:  apagamento LGPD passou a revogar os vínculos no ACS
--
-- O QUE ESTA MIGRATION REPARA
--
-- Até agora o apagamento dos dados pessoais de um paciente removia o dado local e
-- deixava o vínculo cuidadora-paciente **ATIVO** no ACS. A associação "esta cuidadora
-- cuida desta pessoa" sobrevivia ao apagamento da pessoa — e associação é dado.
--
-- O caminho novo já revoga, e falha alto se não conseguir. Mas fechar o caminho não
-- desfaz o passado: no ambiente de desenvolvimento havia 32 pacientes apagados cujos
-- vínculos continuavam ATIVOS.
--
-- SOBRE ESCREVER NUMA TABELA DO ACS
--
-- `neuroassist_global.caregiver_patient_link` pertence ao acs-service, e esta migration
-- está na faixa do PAC. A travessia é deliberada e vale explicar:
--
--   - quem sabe que um paciente foi apagado é o PAC (`paciente.erased_at`), e esse dado
--     vive no schema da empresa — o ACS não o enxerga;
--   - uma migration na faixa do ACS rodaria no schema global, onde não há como saber
--     quais pacientes foram apagados sem SQL dinâmico varrendo todos os schemas;
--   - o recorte por tenant abaixo é exato: cada passagem trata apenas os pacientes do
--     schema em que está rodando.
--
-- É uma reparação pontual de um defeito, **não um padrão a repetir**. O caminho normal
-- é o pac-service chamar `DELETE /api/v1/acs/patients/{id}/links`, que atravessa a
-- invariante D-204 de forma explícita, autenticada e auditada.
--
-- POR QUE ISTO ATRAVESSA A INVARIANTE D-204
--
-- A invariante impede deixar um paciente sem cuidadora, porque o alarme dele precisa ter
-- para onde ir. Um paciente apagado não produz alarme. Aplicar a invariante aqui
-- protegeria um caminho que não existe mais, ao preço de manter viva exatamente a
-- associação que o apagamento se propôs a remover.
-- =============================================================================

-- =============================================================================
-- GUARDA DE PRE-EXISTENCIA — acrescentada depois, e a razao importa
--
-- Esta migration vive na faixa EMPRESA e le uma tabela do schema GLOBAL. Isso
-- tornava o template de empresa NAO AUTOCONTIDO: aplicar o template num tenant
-- novo, num banco onde o global ainda nao tem a tabela de vinculos, estourava
--
--   ERROR: relation "neuroassist_global.caregiver_patient_link" does not exist
--
-- e o provisionamento inteiro falhava. Foi assim que tres testes de
-- `TenantSchemaProvisioningIT` quebraram.
--
-- A guarda diz o que e verdade: esta e uma RECONCILIACAO PONTUAL de dados que ja
-- existiam. Num tenant recem-criado nao ha nada a reconciliar — o certo e nao
-- fazer nada, e nao explodir. Onde a tabela existe, o comportamento e identico
-- ao de antes.
-- =============================================================================
DO $reconcilia$
BEGIN
  IF to_regclass('neuroassist_global.caregiver_patient_link') IS NULL THEN
    RAISE NOTICE 'neuroassist_global.caregiver_patient_link ausente — nada a reconciliar neste schema';
    RETURN;
  END IF;

  UPDATE neuroassist_global.caregiver_patient_link link
  SET status = 'REVOKED',
      -- Marca quando a revogação aconteceu de fato (agora), e não quando o paciente foi
      -- apagado: o vínculo esteve ativo até este instante, e o registro tem de dizer isso.
      revogado_em = now(),
      atualizado_em = now()
  FROM paciente p,
       neuroassist_global.tenant t
  WHERE p.id = link.paciente_id
    AND p.erased_at IS NOT NULL
    AND link.status = 'ACTIVE'
    -- O tenant do vínculo tem de ser o dono DESTE schema. Sem isto, a passagem no schema
    -- da empresa A revogaria vínculos da empresa B — o vazamento entre empresas que o
    -- sistema inteiro existe para impedir.
    AND t.tenant_id = link.tenant_id
    AND t.schema_nome = current_schema();

  -- Nota: não há reconciliação no sentido inverso. Um paciente apagado cujo vínculo já
  -- estava REVOKED permanece como está, e `paciente_cuidador` (a projeção local) é limpo
  -- pelo próprio fluxo de apagamento a partir de agora — as linhas antigas dele já haviam
  -- sido excluídas da V0211 pela cláusula `erased_at IS NULL`.
END
$reconcilia$;
