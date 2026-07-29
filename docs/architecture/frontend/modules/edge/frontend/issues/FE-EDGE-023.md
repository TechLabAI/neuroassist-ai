## [FE-EDGE-023] Playwright e2e — connectivity-derived-freshness.feature + connectivity-poll-failure-unknown.feature (SPEC-08 #4, #5)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/edge-e2e/src/connectivity-derived-freshness.spec.ts` drives a virtual clock through the three
freshness thresholds (`online → stale → offline`), asserting the chip transitions and the "never green past
window" invariant. `connectivity-poll-failure-unknown.spec.ts` forces poll rejection and asserts
`derivedState='unknown'` (degraded, never optimistically `online`).

**Context / Problem statement:**
- Scenario 4 (derived freshness) is the ADR-005 core success signal (doc 01 §7) — the honest middle
  between binary `online/offline`. Virtual clock is REQUIRED to avoid a 90s real wait.
- Scenario 5 (poll failure → unknown) asserts the anti-optimism invariant: a failed poll MUST NOT render
  a green chip; a successful poll on the next tick restores honest state.
- These two scenarios together cover SPEC-03 §2 (derivation rules) and SPEC-04 §3 (ETag/304 + poll failure).

**Approach (execution path):**
**Scenario 4 (freshness):**
1. Stub liveness: fresh response with `serverTime` + `ultimaSincronizacao` aligned (age=0).
2. Override `pollIntervalMs=200ms`, `stalenessThresholdMs=1s`, `offlineWindowMs=2s` via feature flags.
3. Assert chip shows `online` state.
4. Advance Playwright clock by 1.1s → assert chip shows `stale`.
5. Advance by another 1s (total 2.1s) → assert chip shows `offline`.
6. Assert chip NEVER shows `online` after the offline window.

**Scenario 5 (poll failure):**
1. Stub liveness: first N calls succeed (online). Then force all calls to return 500.
2. Assert chip transitions to `unknown` (degraded); chip NOT green.
3. No toast appears (silent retry — SPEC-07 §3).
4. Restore stub to success → next poll tick → chip back to `online`.

**Non-goals:**
- Does not test the pairing flow.
- Does not test backend REST error dialogs (FE-EDGE-027).

**Layer(s):**
frontend / apps/edge-e2e

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (§4, §5)
- `docs/architecture/frontend/modules/edge/frontend/e2e/connectivity-derived-freshness.feature`
- `docs/architecture/frontend/modules/edge/frontend/e2e/connectivity-poll-failure-unknown.feature`

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:e2e`

**Implementation checklist (by file):**

**E2E paths**
- 🔲 `apps/edge-e2e/src/connectivity-derived-freshness.spec.ts`
- 🔲 `apps/edge-e2e/src/connectivity-poll-failure-unknown.spec.ts`

**Tests**
- 🔲 `nx e2e edge-e2e --spec=connectivity-derived-freshness` PASS
- 🔲 `nx e2e edge-e2e --spec=connectivity-poll-failure-unknown` PASS

**Milestone:**
Wave P9 / E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:edge
priority:P9

**Acceptance criteria:**
- Both specs PASS
- Freshness: chip `online` → `stale` → `offline` transitions all asserted; never `online` after window
- Poll failure: chip shows `unknown` (not `online`); no toast; next tick success → `online` restored

**Dependencies:**
- FE-EDGE-017 (ConnectivityChipComponent — the surface under test)
- FE-EDGE-007 (DeviceFacade — deriveState computation)
- FE-EDGE-021 (shared stub infrastructure)

**Data impact:**
- Nenhum (stubbed)

**API / Contract impact:**
- Stub liveness endpoint (GET .../liveness)

**Testing / Evidence required:**
- Playwright report: both specs green
- Virtual clock transition: screenshots at each threshold crossing

**Risks / Notes:**
- `playwright.clock.install()` is available in Playwright ≥ 1.45. Confirm project's Playwright version
  before using fake timers — alternative is feature-flag override to very short thresholds + `waitFor`.
- Server-relative age: the stub must include `serverTime` in the response body (confirmed requirement from
  FE-EDGE-005). If `serverTime` is missing, the test will fall back to client clock and give wrong results.
