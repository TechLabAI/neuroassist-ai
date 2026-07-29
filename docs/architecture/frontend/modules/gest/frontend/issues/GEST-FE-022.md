# GEST-FE-022 -- Playwright e2e — live-test-simple-recognized.feature + live-test-composite.feature

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
Playwright specs for `live-test-simple-recognized.feature` (golden path: simple command recognized via stubbed edge stream) and `live-test-composite.feature` (two-step composite gesture recognized in sequence) pass in `apps/gest-e2e`.

## Context / Problem statement
- Scenarios stub the edge bridge with a fake `EdgeRecognitionSignal` emitter (frozen contract v1.0.0 shape) so the verdict path is deterministic.
- These are the two primary positive scenarios validating SPEC-03 signal → VM mapping.
- Source: SPEC-08 scenarios 1 and 2.

## Approach (execution path)
1. Create `apps/gest-e2e/src/live-test-simple-recognized.spec.ts` with a Playwright test that:
   - Navigates to `/gest?commandDraftRef=test-simple`
   - Intercepts WebSocket/SSE to inject a fake `recognized` signal (simple command)
   - Asserts badge shows "recognized", `GestureTestOutcome.recognized === true`, `degraded === false`
2. Create `apps/gest-e2e/src/live-test-composite.spec.ts`:
   - Injects principal + secundario signals in sequence within timing window
   - Asserts `step` progresses `principal → secundario → recognized`
   - Asserts `GestureTestOutcome.composite === true, recognized === true`
3. Both specs use Playwright's request interception for the bridge stub.

## Non-goals
- Not responsible for real edge agent integration (out of e2e scope for MVP).

## Layer(s)
frontend / e2e

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (scenarios 1, 2)
- `docs/architecture/frontend/modules/gest/frontend/e2e/live-test-simple-recognized.feature`
- `docs/architecture/frontend/modules/gest/frontend/e2e/live-test-composite.feature`

## Architecture alignment
- Playwright (no Cucumber at e2e layer — spec files drive Playwright directly)
- Tag Nx: `scope:gest`, `type:e2e`

## Implementation checklist (by layer/file)

**E2E app**
- [ ] `apps/gest-e2e/src/live-test-simple-recognized.spec.ts`
- [ ] `apps/gest-e2e/src/live-test-composite.spec.ts`

**Tests**
- [ ] `npx nx e2e gest-e2e` PASS (both specs)
- [ ] `npx nx lint gest-e2e` PASS

## Acceptance criteria
- Simple: recognized badge visible, outcome `recognized: true, degraded: false`
- Composite: step indicator advances, outcome `composite: true, recognized: true`
- `npx nx e2e gest-e2e` PASS (with `--skip-install`)

## Dependencies
- GEST-FE-011..016 (all feature components must exist)
- GEST-FE-007, GEST-FE-008 (bridge + facade)

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
- Playwright WebSocket interception may require configuring the `edgeLiveBridge` flag to use a testable transport layer

## Prioridade
P9
