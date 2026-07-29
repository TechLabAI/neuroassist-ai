## [FE-EDGE-015] edgeConductorGuard + authGuard /edge route wiring

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`edgeConductorGuard` in `libs/edge/access` redirects to `/edge/device` (read-only) when the session lacks
the `edge.device.manage` permission, with a "sem permissão" notice. `authGuard` from
`@neuroassist/shared/access-decision` is applied to all `/edge` routes. No route guard leaves a blank page.

**Context / Problem statement:**
- SPEC-01 §3: `edgeConductorGuard` must redirect to `/edge/device` on failure, never produce a blank page
  or leave the user on a 403 empty shell.
- The guard reads the session's permission map — `edge.device.manage` scope per `MODULE_PERMISSION_MAP`
  (SPEC-05 §3). It must NOT block read-only routes.
- SPEC-05 §5: management actions are already hidden in `DeviceActionsComponent` (RBAC). The guard is the
  router-level defense-in-depth layer.
- Bloqueia: FE-EDGE-002 (guard used in EDGE_ROUTES), FE-EDGE-025 (Playwright device-repair-unpair e2e)

**Approach (execution path):**
1. Create `libs/edge/access/src/lib/edge-conductor.guard.ts` as a functional guard.
2. Inject `AccessDecisionService` (from `@neuroassist/shared/access-decision`).
3. Logic: if `session.hasPermission('edge.device.manage')` → `true`; else → `router.createUrlTree(['/edge/device'])`.
4. Optionally: dispatch a toast notification "Você não tem permissão para gerenciar dispositivos."
   (SPEC-07 `insufficient-scope` surface).
5. Create `libs/edge/access/src/index.ts` exporting `edgeConductorGuard`.
6. Reference in `EDGE_ROUTES` pairing route `canActivate: [authGuard, edgeConductorGuard]` (already
   declared in FE-EDGE-002 with placeholder).

**Non-goals:**
- Does not hide UI elements (that is DeviceActionsComponent, FE-EDGE-013).
- Does not implement the shared `authGuard` (from shared/access-decision).

**Layer(s):**
frontend / libs/edge/access

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-01-route-map.md` (§3)
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-05-tool-contract.md` (§3, §5)

**Architecture alignment:**
- Functional guard (Angular 15+)
- Tag Nx: `scope:edge`, `type:domain`

**Implementation checklist (by file):**

**Lib path**
- 🔲 `libs/edge/access/src/lib/edge-conductor.guard.ts`
- 🔲 `libs/edge/access/src/index.ts` (re-export edgeConductorGuard)

**Tests**
- 🔲 Unit: session with `edge.device.manage` → guard returns `true`
- 🔲 Unit: session without `edge.device.manage` → guard returns `UrlTree('/edge/device')`
- 🔲 Unit: redirect target is `/edge/device` (not blank, not `/403`)
- 🔲 `nx test edge-access` PASS
- 🔲 `nx lint edge-access` PASS

**Milestone:**
Wave P5 / Access Guards

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P5

**Acceptance criteria:**
- `npx nx test edge-access` PASS
- `npx nx lint edge-access` PASS
- Guard redirect target is `/edge/device` (asserted by UrlTree inspection in unit test)
- Guard does NOT block `/edge/device` read-only route (authGuard handles that)

**Dependencies:**
- FE-EDGE-001 (MODULE_PERMISSION_MAP defines `edge.device.manage`)
- FE-EDGE-002 (EDGE_ROUTES references edgeConductorGuard)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (reads session state; no HTTP call)

**Testing / Evidence required:**
- Two unit tests: authorized session → `true`; unauthorized → UrlTree redirect
- No blank page: UrlTree is never `false` or `null`

**Risks / Notes:**
- If `AccessDecisionService` is not yet implemented in `@neuroassist/shared/access-decision`, stub it
  as an interface and inject via DI token — do not hard-code localStorage reads.
