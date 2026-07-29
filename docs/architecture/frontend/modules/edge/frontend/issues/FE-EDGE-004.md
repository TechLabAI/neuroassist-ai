## [FE-EDGE-004] edge.bootstrap.ts + apps/edge thin host AppComponent

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`edge.bootstrap.ts` exports `edgeBootstrap` with `init()` and `destroy()`. `init()` resolves the active
patient's device and starts connectivity polling (paused while tab is hidden). `destroy()` stops the poll timer.
The `apps/edge` thin host `AppComponent` calls bootstrap lifecycle via `EdgeShellIntegrationService`.

**Context / Problem statement:**
- `init()` must start from the first authenticated mount so the topbar chip is honest from first paint —
  before the user navigates to any EDGE route (SPEC-06 §4).
- `destroy()` is the last-line guarantee that no `setInterval`/`timer` leaks on module unload.
- Page Visibility API must be wired in `init()` so a backgrounded tab does not hammer the gateway
  (doc 06 R2, SPEC-04 §3).
- Bloqueia: INT-FE-EDGE-001

**Approach (execution path):**
1. Create `libs/edge/shell-entry/src/lib/edge.bootstrap.ts`.
2. `init()`: inject `DeviceFacade`, call `resolveForActivePatient()`, then `startPolling()`.
   Wire `document.addEventListener('visibilitychange', ...)` to pause/resume the poll.
3. `destroy()`: call `DeviceFacade.stopPolling()`, remove the visibilitychange listener.
4. Export `edgeBootstrap` const satisfying the `ModuleBootstrap` interface.
5. In `apps/edge/src/main.ts`, call `edgeBootstrap.init()` after `bootstrapApplication`.
6. Re-export from `libs/edge/shell-entry/src/index.ts`.

**Non-goals:**
- Does not open any pairing flow — pairing is an explicit conductor action (SPEC-06 §4).
- Does not implement `DeviceFacade` internals (FE-EDGE-007).

**Layer(s):**
frontend / libs/edge/shell-entry + apps/edge

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-06-shell-integration.md` (§4)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:app`
- Page Visibility pause aligns with doc 06 R2 (gateway load management)

**Implementation checklist (by file):**

**Lib / App paths**
- 🔲 `libs/edge/shell-entry/src/lib/edge.bootstrap.ts` (edgeBootstrap const)
- 🔲 `libs/edge/shell-entry/src/index.ts` (re-export edgeBootstrap)
- 🔲 `apps/edge/src/main.ts` (call edgeBootstrap.init() post bootstrapApplication)

**Tests**
- 🔲 `libs/edge/shell-entry/src/lib/edge.bootstrap.spec.ts` — init() calls DeviceFacade.resolveForActivePatient() + startPolling(); destroy() calls stopPolling(); visibilitychange:hidden pauses poll; visibilitychange:visible resumes poll
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
- `npx nx build edge` PASS
- Unit: `destroy()` called after `init()` — no leaked timer (spy on `DeviceFacade.stopPolling`)
- Unit: Page Visibility hidden triggers `stopPolling`; visible triggers `startPolling`

**Dependencies:**
- FE-EDGE-003 (EDGE_MODULE_DESCRIPTOR includes bootstrap field)
- FE-EDGE-007 (DeviceFacade, resolveForActivePatient, startPolling, stopPolling)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/edge/patients/{pacienteId}/device` triggered on init (via DeviceFacade)

**Testing / Evidence required:**
- Unit test with mocked DeviceFacade confirming init → startPolling, destroy → stopPolling
- visibilitychange spy test

**Risks / Notes:**
- `empresaContext` (the active patient resolution context) must be available at `init()` time — confirm
  with ACS/session team that `pacienteId` is set before EDGE module bootstraps.
