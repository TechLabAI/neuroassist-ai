## [FE-EDGE-027] EARS gap closure — SPEC-07 missing clause for /errors/device-retired + coverage gaps G1-G5

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Closes the 6 coverage gaps (G1-G6) identified in FE Specs-Validate (M4) that could not be closed by
existing issues. G6 (retry-backoff) is closed by FE-EDGE-019. This issue closes G1-G5 and adds the missing
EARS clause to SPEC-07 for `/errors/device-retired`.

**Context / Problem statement:**
- G1: `DeviceFacade.connectivity` computed signal — the Shell reads this via `connectivityReadModel()`;
  no issue explicitly verified the computed chain `_device → derivedState → connectivity → DeviceConnectivityVM`.
- G2: `PairingFacade.countdown = computed(secondsUntil)` — no unit test for the countdown-to-zero
  → expired transition.
- G3: Closed by FE-EDGE-018 (caregiver not-paired informational path).
- G4: `edgeBootstrap.destroy()` called on module unload — partially covered by FE-EDGE-004 but needs
  an explicit lifecycle integration test.
- G5: `serverTime` in `LivenessView` body — open item from contracts/backend-api-cross-link.md §5;
  no test verifies that the FE derivation falls back correctly if `serverTime` is absent.
- Missing EARS clause (SPEC-07 §1 gap): `IF the backend responds /errors/device-retired, THEN THE SYSTEM
  SHALL display a banner informing the operator the device is retired and SHALL provide a re-register CTA.`

**Approach (execution path):**
1. **SPEC-07 amendment**: add the missing EARS clause for `/errors/device-retired` in the spec file.
2. **G1**: Add unit test in `device.facade.spec.ts` asserting `connectivity()` returns a valid
   `DeviceConnectivityVM` when `_device` has a known `derivedState`.
3. **G2**: Add `secondsUntil.spec.ts` unit test for countdown: positive value; zero; null input.
   Add `pairing.facade.spec.ts` test: `countdown() ≤ 0` triggers `phase → expired`.
4. **G4**: Integration test: call `edgeBootstrap.init()` → `destroy()` in sequence; assert `stopPolling()`
   called and no pending timers (use `fakeAsync` + `discardPeriodicTasks()`).
5. **G5**: Unit test in `derive-state.spec.ts`: `serverTime` absent from response → `derivedState = 'unknown'`
   (safe fallback, not a crash or client-clock assumption).

**Non-goals:**
- Does not re-implement any existing component.
- Does not add new production code (only tests + SPEC amendment).

**Layer(s):**
frontend / libs/edge/data-access (tests) + docs/architecture/frontend/modules/edge/frontend/frontend-spec/ (SPEC amendment)

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-07-error-handling.md` (§1 — add missing clause)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:data-access` (test additions)

**Implementation checklist (by file):**

**Spec amendment**
- 🔲 `SPEC-07-error-handling.md` — add EARS clause for `/errors/device-retired`

**Tests**
- 🔲 `device.facade.spec.ts` — G1: connectivity() computed chain test
- 🔲 `libs/edge/data-access/src/lib/seconds-until.spec.ts` — G2: countdown function
- 🔲 `pairing.facade.spec.ts` — G2: countdown ≤ 0 → phase = expired
- 🔲 `edge.bootstrap.spec.ts` — G4: init+destroy no leaked timer (fakeAsync)
- 🔲 `derive-state.spec.ts` — G5: serverTime absent → unknown (not crash)
- 🔲 `nx test edge-shell-entry edge-data-access` PASS

**Milestone:**
Wave P9 / E2E + Hardening

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P9

**Acceptance criteria:**
- `npx nx test edge-data-access edge-shell-entry` PASS (all G-gap tests green)
- SPEC-07 amended: `device-retired` EARS clause present
- G5: no crash on `serverTime` absent — `unknown` returned
- G2: countdown to 0 → `expired` transition confirmed in unit test

**Dependencies:**
- FE-EDGE-007 (DeviceFacade, deriveState, connectivity computed)
- FE-EDGE-008 (PairingFacade, countdown, phase transition)
- FE-EDGE-004 (edge.bootstrap.ts, destroy lifecycle)

**Data impact:**
- Nenhum

**API / Contract impact:**
- G5 tests the behavior when BE omits `serverTime` from response body (defensive coding)

**Testing / Evidence required:**
- 5 new test cases all green in CI (one per gap G1, G2 ×2, G4, G5)
- SPEC-07 diff shows new EARS clause

**Risks / Notes:**
- G5 (`serverTime` absent): coordinate with BE to confirm whether `serverTime` can ever be absent in
  production (should not be per api-contract §1.1). The FE fallback is defensive, not a supported path.
