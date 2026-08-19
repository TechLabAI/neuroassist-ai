# HIST-FE-001 -- Lint: corrigir selector de componente e remover imports nao utilizados

**Status:** DONE -- concluido 2026-08-19 (P1)
**Tipo:** BUG
**Maturidade:** 3 -- Implementado e verificado
**Done/Total:** 3/3 itens

## Contexto

O modulo HIST foi scaffolded diretamente sem passar pelo pipeline architect formal. O componente `HistoryTimelineComponent` usava o selector `hist-history-timeline`, que viola a regra `@angular-eslint/component-selector` configurada em `frontend/libs/hist/.eslintrc.json` (prefix obrigatorio: `lib`). Alem disso, dois imports nao utilizados foram identificados: `signal` em `history-timeline.component.ts` e `takeUntilDestroyed` em `hist.facade.ts`. Esses warnings + 1 error impediam que o gate de lint passasse, bloqueando PR.

## Spec de referencia

- `docs/architecture/frontend/standards/nx-boundaries.md` — selector prefix rule
- `frontend/libs/hist/.eslintrc.json` — `@angular-eslint/component-selector` com `prefix: "lib"`
- Plano: `docs/architecture/frontend/plans/plan-FE-HIST.md` — P1

## Escopo

- [x] Corrigir `selector: 'hist-history-timeline'` → `selector: 'lib-history-timeline'` em `history-timeline.component.ts`
- [x] Remover import `signal` nao utilizado de `@angular/core` em `history-timeline.component.ts`
- [x] Remover import `takeUntilDestroyed` (e `DestroyRef`) nao utilizado de `hist.facade.ts`

## Resultado esperado

`npx nx lint hist` retorna exit 0 com 0 errors e 0 warnings.

## Criterios de aceite

- [x] `cd frontend && npx nx lint hist` — exit code 0, nenhum erro, nenhum warning
- [x] Selector do componente usa prefixo `lib-` conforme `.eslintrc.json`
- [x] Nenhum import nao utilizado nos arquivos modificados

## Arquivos afetados

- `frontend/libs/hist/src/lib/feature-timeline/history-timeline.component.ts`
- `frontend/libs/hist/src/lib/hist.facade.ts`

## Dependencias

Nenhuma.

## Estimativa

XS

## Prioridade

P1
