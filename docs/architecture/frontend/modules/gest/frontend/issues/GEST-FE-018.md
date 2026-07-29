# GEST-FE-018 -- gest/navigation — breadcrumb 'Calibração › Teste ao vivo' for thin host route only

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
`libs/gest/navigation/src/lib/gest-breadcrumb.provider.ts` provides the breadcrumb trail `Calibração › Teste ao vivo` for the thin host route (`/gest`). When composed into PAC/onboarding pages, GEST contributes no breadcrumb (host owns the trail).

## Context / Problem statement
- GEST has no top-level nav and contributes no breadcrumb in production composition. The breadcrumb exists only for the thin host dev/e2e app.
- Source: SPEC-01 §1, §2.

## Approach (execution path)
1. Create `gest-breadcrumb.provider.ts` providing a route `title` and breadcrumb data via Angular `Route.data`.
2. Add `data: { breadcrumb: ['Calibração', 'Teste ao vivo'] }` to the thin host route in `GEST_ROUTES`.
3. Export from `libs/gest/navigation/src/index.ts`.

## Non-goals
- Not responsible for breadcrumbs when GEST is composed into PAC or onboarding (host manages).

## Layer(s)
frontend / navigation

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-01-route-map.md` (§2)

## Architecture alignment
- Route `data` object, no Angular class required
- Tag Nx: `scope:gest`, `type:domain`

## Implementation checklist (by layer/file)

**Navigation lib**
- [ ] `libs/gest/navigation/src/lib/gest-breadcrumb.provider.ts`

**Tests**
- [ ] `npx nx test gest-navigation` PASS
- [ ] `npx nx lint gest-navigation` PASS
- [ ] `npx nx build gest-navigation` PASS

## Acceptance criteria
- Thin host `/gest` route carries breadcrumb `['Calibração', 'Teste ao vivo']`
- Composed mode contributes no breadcrumb data
- `npx nx build gest-navigation` PASS

## Dependencies
- GEST-FE-002 (GEST_ROUTES wires the route data)

## Empresa-context impact
Nenhum.

## Milestone
Wave 6 / Navigation

## Labels
type:feature
layer:frontend
scope:gest
priority:P6

## Risks / Notes
- Breadcrumb must ONLY appear in the thin host — composition-boundary.feature e2e scenario (GEST-FE-025) verifies no breadcrumb when composed

## Prioridade
P6
