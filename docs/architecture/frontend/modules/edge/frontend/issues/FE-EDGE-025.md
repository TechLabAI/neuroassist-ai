## [FE-EDGE-025] Playwright e2e — shell-chip-composition.feature (SPEC-08 #7): topbar chip, bootstrap init/destroy, nx-boundary

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/edge-e2e/src/shell-chip-composition.spec.ts` asserts structural composition: the topbar chip renders
from the read-model on every authenticated route; chip click navigates to `/edge/device`; bootstrap
`init()` resolves the device and starts polling; `destroy()` stops the timer (no leaked interval on logout).
The nx-boundary ESLint rule forbidding direct `scope:edge` imports from shell internals is referenced.

**Context / Problem statement:**
- SPEC-08 #7 is a structural/composition assertion — it validates the ADR-006 composition pattern at the
  integration level (not just unit level).
- Bootstrap `destroy()` must be called on logout/session end — no leaked poll interval. This is tested
  by simulating navigation away and asserting no further HTTP poll requests.
- The nx-boundary ESLint rule is separately enforced at `nx lint`; the e2e references it for traceability.

**Approach (execution path):**
1. Authenticate as caregiver; navigate to home route (not `/edge/*`).
2. Assert: `ConnectivityChipComponent` visible in topbar.
3. Assert: chip reflects device `derivedState` from liveness stub.
4. Click chip → assert navigation to `/edge/device`.
5. Navigate away (logout simulation) → assert liveness poll stops (stub call count does not increase).
6. Login redirect: clear session → navigate to `/edge/device` → assert redirect to login (authGuard).

**Non-goals:**
- Does not test the onboarding step-3 composition (INT-FE-EDGE-003).
- Does not test connectivity state transitions in depth (FE-EDGE-023).

**Layer(s):**
frontend / apps/edge-e2e

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (§7)
- `docs/architecture/frontend/modules/edge/frontend/e2e/shell-chip-composition.feature`

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:e2e`

**Implementation checklist (by file):**

**E2E paths**
- 🔲 `apps/edge-e2e/src/shell-chip-composition.spec.ts`

**Tests**
- 🔲 `nx e2e edge-e2e --spec=shell-chip-composition` PASS
- 🔲 `nx lint edge-shell-entry` PASS (nx-boundary: no scope:web direct imports confirmed)

**Milestone:**
Wave P9 / E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:edge
priority:P9

**Acceptance criteria:**
- Spec PASS
- Chip visible on non-EDGE route (home)
- Chip click navigates to `/edge/device`
- After module destroy: liveness stub call count frozen (no further polls)
- Login redirect: unauthenticated `/edge/device` → login page (not blank)

**Dependencies:**
- FE-EDGE-017 (ConnectivityChipComponent)
- FE-EDGE-004 (edge.bootstrap.ts — init/destroy lifecycle)
- INT-FE-EDGE-001 (EdgeShellIntegrationService — composition point)
- FE-EDGE-021 (shared stubs infrastructure)

**Data impact:**
- Nenhum (stubbed)

**API / Contract impact:**
- Stubs: GET .../liveness (patient), GET .../patients/{id}/device

**Testing / Evidence required:**
- Playwright report: spec green
- Poll stop: stub request count does not increase after destroy
- Login redirect: URL asserted to be login route after clear-session

**Risks / Notes:**
- Logout simulation in Playwright: clear auth cookie/token directly (page.context().clearCookies()) rather
  than clicking a logout button to keep the test focused on module lifecycle.
- nx-boundary lint is already covered by `nx lint` in CI — this e2e spec should NOT duplicate it;
  just reference it as a traceability link in comments.
