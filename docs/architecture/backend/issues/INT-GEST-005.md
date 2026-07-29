# INT-GEST-005 -- Integration with MIG — contribute GEST DDL (V0400..V0499) to centralized migration service

**Status:** INTACT -- gerado 2026-07-27
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/3 itens

## Contexto
GEST nao roda Flyway localmente. O DDL e contribuido ao servico centralizado `neuroassist-migrations` (MIG). O `be-migration-generator.py` extrai os blocos SQL de `data-model.md` e gera `V0400__gest_initial_schema.sql`. O MIG aplica essa migration para cada novo tenant no provisionamento. GEST deve iniciar sem erro se o schema `t_<tenantId>` ja existir.

## Spec de referencia
- hub-integration.md §3, data-model.md §5, SPEC-04

## Escopo
- [ ] validar que `V0400__gest_initial_schema.sql` (gerado em GEST-013) e copiado ou linkado para o MIG migration set
- [ ] validar que MIG aplica corretamente os 4 DDLs GEST em um schema de tenant vazio (sem erros)
- [ ] validar que `gest-service` falha com `503` (nao 500 raw) quando o MIG ainda nao aplicou o schema do tenant (GEST-014 guard)

## Resultado esperado
Novo tenant provisionado via MIG tem schema GEST pronto; `gest-service` nao precisa de Flyway local.

## Criterios de aceite
- [ ] MIG aplica `V0400__gest_initial_schema.sql` sem erros em schema vazio (teste de integracao ou manual)
- [ ] `gest-service` responde `GET /tipos-gesto` com 200 apos MIG aplicar schema (nao antes)
- [ ] `gest-service` responde `503` (nao 500) para tenant cujo schema ainda nao foi criado pelo MIG

## Dependencias
GEST-013 (migration gerada), GEST-014 (503 guard).

## Estimativa
S

## Prioridade
P3
