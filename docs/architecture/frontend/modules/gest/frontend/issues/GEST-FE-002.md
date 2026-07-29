# GEST-FE-002 -- GEST_ROUTES — thin host app entry (apps/gest, dev/e2e only)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens
**GitHub:** [#TBD]

## Outcome
`apps/gest` thin host app and `GEST_ROUTES` exist so `nx serve gest`, `nx build gest`, and `apps/gest-e2e` Playwright tests have a web-server base. This route is NOT wired into the shell nav (`showInNav: false`).

## Context / Problem statement
- GEST has no standalone product route in the shell nav. The thin host exists only for dev/e2e purposes, allowing `GestureLiveTestComponent` to be tested standalone.
- Optional `?commandDraftRef=<id>` query param available in dev mode; in production the draft arrives via `@Input`.
- Source: SPEC-01 §1, §2.
- Bloqueia: GEST-FE-011 (component must mount in a route context), GEST-FE-022..026 (e2e needs a host)

## Approach (execution path)
1. Scaffold `apps/gest/src/app/app.routes.ts` with `GEST_ROUTES` containing a single lazy route: `path: 'gest'`, `loadComponent: () => import('@neuroassist/gest').then(m => m.GestureLiveTestComponent)`, guards `[authGuard, gestDeviceOnlineGuard]`.
2. Add optional `commandDraftRef` query param handling in the thin host component.
3. Wire `app.config.ts` with `provideRouter(GEST_ROUTES)`.
4. Export `GEST_ROUTES` from `libs/gest/shell-entry/src/lib/gest.routes.ts` (also consumed by descriptor).
5. Confirm `project.json` has `tags: ["scope:gest", "type:app"]`.

## Non-goals
- Not responsible for composing GEST into PAC/onboarding pages (GEST-INT-005, GEST-INT-006).
- The thin host has no navigation menu, breadcrumb state is handled by GEST-FE-018.

## Layer(s)
frontend / tool entry

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-01-route-map.md` (§1..§4)

## Architecture alignment
- Standalone + OnPush + Signals
- Tag Nx: `scope:gest`, `type:app`
- `showInNav: false` enforced in MODULE_MENU_METADATA (GEST-FE-001)

## Implementation checklist (by layer/file)

**App entry**
- [ ] `apps/gest/src/app/app.routes.ts`
- [ ] `apps/gest/src/app/app.config.ts`
- [ ] `apps/gest/src/app/app.component.ts`
- [ ] `libs/gest/shell-entry/src/lib/gest.routes.ts`

**Tests**
- [ ] `npx nx test gest` PASS
- [ ] `npx nx lint gest` PASS
- [ ] `npx nx build gest` PASS

## Acceptance criteria
- `nx serve gest` starts without errors
- Route `/gest` mounts `GestureLiveTestComponent` when authenticated
- Unauthenticated access redirects to login (authGuard)
- `npx nx build gest` PASS (used by e2e baseline)

## Dependencies
- GEST-FE-001 (MODULE_MENU_METADATA.showInNav must be false)
- GEST-FE-011 (GestureLiveTestComponent)
- GEST-FE-017 (gestDeviceOnlineGuard)

## Empresa-context impact
Nenhum — thin host route has no tenant-scoped state.

## Milestone
Wave 1 / Foundation

## Labels
type:feature
layer:frontend
scope:gest
priority:P1

## Risks / Notes
- `commandDraftRef` query param must NEVER be used in production (draft arrives via `@Input` in composed contexts to prevent biometric-adjacent context leaking into URLs)

## Prioridade
P1
