## [FE-EDGE-019] G6 retry-backoff — explicit tests for action operations (register/pair/unpair/retire) with 2-attempt backoff + toast

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
The retry-backoff strategy for EDGE action operations (register, pairToPatient, unpair, retire) is explicitly
tested: 2 automatic attempts via `shared/access-http-base` interceptor, then a user-facing toast with a manual
retry. This closes coverage gap G6 (high priority) identified in FE Specs-Validate (M4).

**Context / Problem statement:**
- SPEC-07 §3: action network/5xx → automatic retry with backoff (2 attempts), then toast with manual retry.
- G6 (Specs-Validate finding): no explicit test coverage existed for the 2-attempt backoff path; the
  retry interceptor in `shared/access-http-base` was assumed to be correct without EDGE-specific test.
- The gap is HIGH PRIORITY because incorrect retry behavior could leave the operator with no feedback
  when pairing fails due to transient network — a safety-critical UX gap in a healthcare context.
- This issue adds EDGE-specific integration tests that drive the real retry interceptor with a mock
  `HttpClient` to confirm the 2-attempt + toast sequence for each action operation.
- Bloqueia: FE-EDGE-021 (Playwright backend-error-handling needs retry confirmed at unit level first)

**Approach (execution path):**
1. Create `libs/edge/data-access/src/lib/edge-http-retry.spec.ts` (integration-level, uses
   `HttpClientTestingModule` + `HTTP_INTERCEPTORS` with the real `RetryInterceptor` from shared).
2. For each action operation (`register`, `pairToPatient`, `unpair`, `retire`):
   - Simulate 2 × 500 (server error) responses → confirm the interceptor retried once before bubbling.
   - Simulate 3rd response as success → confirm the operation succeeded after 2 fails.
   - Simulate 3 × 500 → confirm the observable errors (bubbles to facade error handler).
3. Verify toast: in `PairingFacade.register()` and `DeviceFacade.unpair()` specs, assert that after
   exhaustion the `ToastService` is called with the "Falha temporária de conexão. Tentando novamente…" message
   and a retry callback.
4. Confirm: liveness poll does NOT trigger the toast on failure (silent retry — SPEC-07 §3 says only
   action ops get the toast; poll failures are `unknown` state, not a toast).

**Non-goals:**
- Does not implement the retry interceptor (lives in `shared/access-http-base`).
- Does not add backoff delay implementation — confirms existing interceptor behavior is wired.

**Layer(s):**
frontend / libs/edge/data-access (tests only — no new production code unless wiring gap is found)

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-07-error-handling.md` (§3 Retry strategy)

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:data-access`
- Test-only issue (production code should already be correct via shared interceptor)

**Implementation checklist (by file):**

**Tests**
- 🔲 `libs/edge/data-access/src/lib/edge-http-retry.spec.ts`:
  - `register()`: 2 × 500 then success → succeeds on 3rd attempt
  - `register()`: 3 × 500 → observable errors
  - `pairToPatient()`: 2 × 500 then success → succeeds
  - `unpair()`: 3 × 500 → `ToastService` called with retry message
  - `retire()`: 3 × 500 → `ToastService` called with retry message
  - Liveness poll failure → `ToastService` NOT called (silent → `unknown`)
- 🔲 `nx test edge-data-access` PASS (all existing + new retry tests)
- 🔲 `nx lint edge-data-access` PASS

**Milestone:**
Wave P7 / UX Polish (HIGH PRIORITY — closes G6)

**Labels:**
type:feature
layer:frontend
scope:edge
priority:P7

**Acceptance criteria:**
- `npx nx test edge-data-access` PASS (including all 6 retry scenarios)
- `npx nx lint edge-data-access` PASS
- G6 explicitly closed: all 4 action operations have 2-attempt retry coverage
- Liveness poll: `ToastService` NOT called on poll failure (confirmed by spy assertion)

**Dependencies:**
- FE-EDGE-006 (EdgeHttpService — operations under test)
- FE-EDGE-007 (DeviceFacade — unpair/retire error path with toast)
- FE-EDGE-008 (PairingFacade — register error path with toast)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Tests confirm behavior against `transient` error kind (SPEC-07 §1 table)

**Testing / Evidence required:**
- 6 test cases in retry spec all green in CI
- `ToastService.show` spy: called once after exhaustion (not once per retry attempt)

**Risks / Notes:**
- If `RetryInterceptor` from `shared/access-http-base` is not yet implemented, this issue BLOCKS until
  that interceptor is shipped (create a prerequisite ticket in shared backlog).
- Backoff delay: if the interceptor uses real `delay()`/`timer()`, tests need fake async (`fakeAsync`/
  `tick()`) to avoid slow test suite. Confirm interceptor uses a configurable delay token.
