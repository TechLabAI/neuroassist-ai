# GEST-FE-026 -- stream-no-cache-between-runs.feature — SPEC-04 §5 gap (M4-fe backlog issue)

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens
**GitHub:** [#TBD]

## Outcome
`apps/gest-e2e/src/stream-no-cache-between-runs.spec.ts` covers the invariant from SPEC-04 §5: "THE SYSTEM SHALL NOT cache or replay any recognition stream between test runs." The corresponding `.feature` file is added to `docs/architecture/frontend/modules/gest/frontend/e2e/`. Gate 2 coverage reaches 33/33 (100%).

## Context / Problem statement
- This issue is **mandatory**: it was identified as the sole real gap in the M4-fe Gate 2 coverage audit (32/33, 97%). SPEC-04 §5 clause "THE SYSTEM SHALL NOT cache or replay any recognition stream between test runs" had no corresponding feature.
- The invariant is critical: if a previous test's stream is replayed in a new run, a false "recognized" verdict could be shown — violating the honest-degraded guarantee and potentially triggering an emergency command based on stale data.
- Source: SPEC-04 §5; M4-fe handoff warning.
- This closes the coverage gap to 33/33.

## Approach (execution path)
1. Create feature file `docs/architecture/frontend/modules/gest/frontend/e2e/stream-no-cache-between-runs.feature` with:
   - Scenario: "Dado que o cuidador concluiu um teste de gesto, Quando iniciar um novo teste, Então o sistema NÃO deve reproduzir sinais do teste anterior"
   - Asserting both `EdgeDetectionClient` and `GestureTestFacade` use a fresh subscription on each `startTest()`.
2. Create `apps/gest-e2e/src/stream-no-cache-between-runs.spec.ts`:
   - Run test 1; inject a `recognized` signal; verify outcome.
   - Stop. Run test 2; inject NO signal.
   - Assert: test 2 does NOT show "recognized" from test 1's signal.
   - Assert: `EdgeDetectionClient.openStream()` called twice (new subscription per run).
3. Use spy on `EdgeDetectionClient.openStream` to confirm fresh call on each `startTest`.

## Non-goals
- Not a duplicate of `camera-lifecycle-teardown.feature` (that tests MediaStream track termination; this tests signal stream isolation between runs).

## Layer(s)
frontend / e2e

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-04-data-access.md` (§5, last EARS clause)

## Architecture alignment
- Playwright with test isolation
- Tag Nx: `scope:gest`, `type:e2e`

## Implementation checklist (by layer/file)

**E2E app**
- [ ] `apps/gest-e2e/src/stream-no-cache-between-runs.spec.ts`

**Feature doc (gap closure)**
- [ ] `docs/architecture/frontend/modules/gest/frontend/e2e/stream-no-cache-between-runs.feature`

**Tests**
- [ ] `npx nx e2e gest-e2e` PASS
- [ ] `npx nx lint gest-e2e` PASS
- [ ] Gate 2 coverage: 33/33 (100%) after this issue

## Acceptance criteria
- Feature file covers SPEC-04 §5 EARS clause verbatim
- Test 2 does NOT show recognized verdict from test 1's stale stream signal
- `EdgeDetectionClient.openStream` called once per `startTest()` (no reuse)
- `npx nx e2e gest-e2e` PASS
- Gate 2 fe-ears-coverage.py: 33/33 after adding this feature

## Dependencies
- GEST-FE-007 (EdgeDetectionClient — fresh subscription behavior)
- GEST-FE-008 (GestureTestFacade — `startTest` always opens a new stream)

## Empresa-context impact
Nenhum.

## Milestone
Wave 9 / e2e + Hardening

## Labels
type:feature
layer:frontend
scope:gest
priority:P9

## Risks / Notes
- **This is the M4-fe mandatory backlog item.** Do not close this issue without verifying Gate 2 reaches 33/33.
- If `GestureTestFacade.retry()` reuses the same subscription instead of calling `openStream` again, this test will catch it.

## Prioridade
P9
