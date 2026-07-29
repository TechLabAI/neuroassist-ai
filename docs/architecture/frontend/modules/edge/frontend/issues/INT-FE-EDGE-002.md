## [INT-FE-EDGE-002] Shell topbar connectivity chip wiring (scope:web) — bind read-model, nx-boundary enforcement

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
The `scope:web` shell topbar component binds `[derivedState]`, `[tone]`, `[label]` from
`EdgeShellIntegrationService.connectivityReadModel()` signal to `ConnectivityChipComponent`, and routes
the `(open)` output to `router.navigate(['/edge/device'])`. The nx-boundary ESLint rule is verified so no
scope:web internal directly imports scope:edge.

**Context / Problem statement:**
- The topbar is in `scope:web` — it may compose `scope:edge` outputs only through the descriptor/slot
  (ADR-006, SPEC-06 §5). A direct `import { DeviceFacade } from '@neuroassist/edge'` in the topbar is
  a boundary violation.
- The prototype `topbar.component.ts` reads `DispositivosFacade.items()[0].status` directly (raw flag) —
  this is the thing being made honest: replaced by the derived read-model from `EdgeShellIntegrationService`.
- The `ConnectivityChipComponent` is exported via `@neuroassist/edge` barrel; the topbar imports it
  through the descriptor, not by path.

**Approach (execution path):**
1. In `apps/web/src/app/topbar/topbar.component.ts`:
   - Inject `EdgeShellIntegrationService`.
   - Declare `edgeConnectivity = inject(EdgeShellIntegrationService).connectivityReadModel()`.
   - Add `<edge-connectivity-chip [derivedState]="edgeConnectivity().derivedState" [tone]="edgeConnectivity().tone" [label]="edgeConnectivity().label" (open)="router.navigate(['/edge/device'])"/>`.
2. Import `ConnectivityChipComponent` via the barrel export (not internal path).
3. Add/verify nx-boundary ESLint rule: `scope:web` may import `scope:edge` only through
   `@neuroassist/edge` barrel — no direct `libs/edge/**/*` path imports.
4. Run `nx lint web` to confirm no boundary violation.

**Non-goals:**
- Does not implement `ConnectivityChipComponent` (FE-EDGE-017).
- Does not implement `EdgeShellIntegrationService` (INT-FE-EDGE-001).

**Layer(s):**
frontend / apps/web (scope:web shell topbar)

**Bounded context:**
edge + web

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-06-shell-integration.md` (§5)

**Architecture alignment:**
- Tag Nx: `scope:web`, `type:app`
- nx-boundary: `scope:web` imports `scope:edge` only via barrel

**Implementation checklist (by file):**

**App path**
- 🔲 `apps/web/src/app/topbar/topbar.component.ts` (inject EdgeShellIntegrationService, bind chip)
- 🔲 `apps/web/src/app/topbar/topbar.component.html` (add edge-connectivity-chip element)

**Tests**
- 🔲 Unit: topbar renders `<edge-connectivity-chip>` with correct inputs from read-model
- 🔲 Unit: `(open)` triggers `router.navigate(['/edge/device'])`
- 🔲 `nx lint web` PASS (no boundary violation)
- 🔲 `nx test web` PASS

**Milestone:**
Wave P4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:edge
priority:P4

**Acceptance criteria:**
- `npx nx lint web` PASS (no scope:web→scope:edge internal import violations)
- Topbar unit: chip inputs bound from read-model signal
- `(open)` routes to `/edge/device`

**Dependencies:**
- INT-FE-EDGE-001 (EdgeShellIntegrationService.connectivityReadModel)
- FE-EDGE-017 (ConnectivityChipComponent — the component being rendered)

**Data impact:**
- Nenhum

**API / Contract impact:**
- None (composition wiring; HTTP is inside DeviceFacade)

**Testing / Evidence required:**
- `nx lint` boundary check: confirm no internal path import
- Unit test: chip visible in topbar with stubbed read-model

**Risks / Notes:**
- The prototype topbar reads the raw `status` flag. This PR replaces that read — coordinate with the
  DASH/Web team to ensure no other topbar feature is broken by the change.
