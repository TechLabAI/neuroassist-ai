# HIST-FE-002 -- Unit tests scaffold: cobertura baseline da facade, component, guard e mock adapter

**Status:** DONE -- concluido 2026-08-19 (P2)
**Tipo:** TEST
**Maturidade:** 3 -- Implementado e verificado
**Done/Total:** 4/4 arquivos; 32/32 testes PASS

## Contexto

O modulo HIST foi scaffolded sem arquivos `.spec.ts`. O target `npx nx test hist` passava com `passWithNoTests: true`, mascarando cobertura zero. Sem testes, nao ha evidencia verificavel de corretude da facade, do componente ou dos adapters mock. Esta issue cria o scaffold de testes unitarios cobrindo os contratos comportamentais principais do modulo.

## Spec de referencia

- `docs/architecture/frontend/standards/nx-conventions.md` — unit test policy
- Plano: `docs/architecture/frontend/plans/plan-FE-HIST.md` — P2
- `frontend/libs/hist/src/lib/hist.facade.ts` — contratos de comportamento testados
- `frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.ts` — componente testado

## Escopo

- [x] Criar `frontend/libs/hist/src/lib/hist.facade.spec.ts` — 8 cenarios: load, empty, error, setPeriod, loadMore, markFeedback (sucesso + 409), retry
- [x] Criar `frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.spec.ts` — 8 cenarios: render titulo, abas de periodo, estados (loading/error/empty/loaded), interacao (retry, setPeriod)
- [x] Criar `frontend/libs/hist/src/lib/access/hist.guard.spec.ts` — 1 cenario: histGuard retorna true (Wave-0 bypass)
- [x] Criar `frontend/libs/hist/src/lib/api/hist-client-mock.adapter.spec.ts` — contratos do mock client (list, get, verify)

## Resultado esperado

`npx nx test hist` retorna exit 0 com >= 20 testes PASS (realizado: 32 testes).

## Criterios de aceite

- [x] `cd frontend && npx nx test hist` — exit code 0
- [x] >= 20 testes PASS (realizado: 32 PASS, 0 FAIL)
- [x] Todos os cenarios criticos cobertos: load states, feedback non-optimistic, 409 reconciliation, Wave-0 guard

## Arquivos criados

- `frontend/libs/hist/src/lib/hist.facade.spec.ts` (248 linhas)
- `frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.spec.ts` (227 linhas)
- `frontend/libs/hist/src/lib/access/hist.guard.spec.ts` (140 linhas)
- `frontend/libs/hist/src/lib/api/hist-client-mock.adapter.spec.ts` (176 linhas)

## Dependencias

HIST-FE-001 (lint PASS antes de criar testes para nao interferir).

## Estimativa

M

## Prioridade

P2
