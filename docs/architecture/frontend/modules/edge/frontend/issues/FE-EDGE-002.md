## [FE-EDGE-002] edge.routes.ts + edge.module.ts — EDGE_ROUTES, EdgeModule barrel, thin-host app entry

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EDGE_ROUTES` is the lazy route configuration for the `apps/edge` thin host (redirect, device-detail, pairing).
`EdgeModule` (or `provideEdge()`) is the barrel exported from `@neuroassist/edge`. The `apps/edge` app shell
has a minimal `AppComponent` that loads children via `EDGE_ROUTES` for dev/e2e serving.

**Context / Problem statement:**
- `/edge` redirects to `/edge/device`; `/edge/device` and `/edge/device/:deviceId` lazy-load
  `DeviceDetailPageComponent`; `/edge/pairing` lazy-loads `DevicePairingPageComponent` behind
  `[authGuard, edgeConductorGuard]` (SPEC-01).
- The thin host exists for `nx serve edge` (dev) and `apps/edge-e2e` (Playwright). It is NOT the shell nav
  entry — the shell composes EDGE via descriptor (FE-EDGE-003).
- Bloqueia: FE-EDGE-004, FE-EDGE-012, FE-EDGE-014, INT-FE-EDGE-004

**Approach (execution path):**
1. Create `libs/edge/shell-entry/src/lib/edge.routes.ts` with `EDGE_ROUTES`:
   - `{ path: '', redirectTo: 'device', pathMatch: 'full' }`
   - `{ path: 'device', loadComponent: () => import(...DeviceDetailPageComponent), canActivate: [authGuard] }`
   - `{ path: 'device/:deviceId', loadComponent: () => import(...DeviceDetailPageComponent), canActivate: [authGuard] }`
   - `{ path: 'pairing', loadComponent: () => import(...DevicePairingPageComponent), canActivate: [authGuard, edgeConductorGuard] }`
2. Create `libs/edge/shell-entry/src/lib/edge.module.ts` exporting `EdgeModule` (re-exports barrel:
   `EDGE_ROUTES`, `EDGE_MODULE_DESCRIPTOR`, `DevicePairingPageComponent`, `edgeConnectivityReadModel`).
3. Verify `apps/edge/src/app/app.component.ts` uses `RouterOutlet` + `EDGE_ROUTES` for thin host.
4. Add barrel entries to `libs/edge/shell-entry/src/index.ts`.

**Non-goals:**
- Does not implement guard logic (FE-EDGE-015).
- Does not implement the components themselves (FE-EDGE-012, FE-EDGE-014).

**Layer(s):**
frontend / libs/edge/shell-entry + apps/edge

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-01-route-map.md` (§2, §3)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:edge`, `type:app`
- `loadComponent` lazy routes (not `loadChildren` from child module)

**Implementation checklist (by file):**

**Lib / App paths**
- 🔲 `libs/edge/shell-entry/src/lib/edge.routes.ts` (EDGE_ROUTES const)
- 🔲 `libs/edge/shell-entry/src/lib/edge.module.ts` (EdgeModule barrel)
- 🔲 `libs/edge/shell-entry/src/index.ts` (re-export EDGE_ROUTES, EdgeModule)
- 🔲 `apps/edge/src/app/app.component.ts` (RouterOutlet + EDGE_ROUTES)
- 🔲 `apps/edge/src/app/app.config.ts` (provideRouter(EDGE_ROUTES))

**Tests**
- 🔲 `libs/edge/shell-entry/src/lib/edge.routes.spec.ts` — route table has /edge/device + /edge/pairing; pairing route has 2 guards; redirect present
- 🔲 `nx test edge-shell-entry` PASS
- 🔲 `nx lint edge-shell-entry` PASS

**Milestone:**
Wave P1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P1

**Acceptance criteria:**
- `npx nx test edge-shell-entry` PASS
- `npx nx lint edge-shell-entry` PASS
- `npx nx build edge` PASS (thin host builds without errors)
- Route table asserted: `/edge/pairing` has `canActivate: [authGuard, edgeConductorGuard]`

**Dependencies:**
- FE-EDGE-001 (contract — MODULE_KEY must exist to type the descriptor)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None at this layer; routes reference components that bind to edge-service (resolved in P2/P3)

**Testing / Evidence required:**
- Route table unit test passing
- `nx build edge` clean output

**Risks / Notes:**
- Confirm `edgeConductorGuard` import path from `libs/edge/access` after FE-EDGE-015 is done;
  use a placeholder/token until P5 is complete.
