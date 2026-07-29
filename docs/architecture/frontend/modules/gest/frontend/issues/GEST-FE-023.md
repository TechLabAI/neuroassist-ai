# GEST-FE-023 -- Playwright e2e — live-test-degraded-offline.feature + live-test-timeout-not-recognized.feature

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
Playwright specs for `live-test-degraded-offline.feature` (device offline → honest degraded mode, no fake recognized) and `live-test-timeout-not-recognized.feature` (no signal within window → `not-recognized`) pass in `apps/gest-e2e`.

## Context / Problem statement
- Scenario 3 forces bridge unreachable (`edgeLiveBridge` off or device offline) and asserts `degraded: true` and the ABSENCE of a real `recognized` outcome.
- Scenario 6 stubs the timing window to expire without any signal and asserts `state: 'not-recognized'`.
- Source: SPEC-08 scenarios 3 and 6.

## Approach (execution path)
1. `live-test-degraded-offline.spec.ts`: set `edgeLiveBridge` flag to off (or block WS connection); start test; assert degraded banner visible; assert `GestureTestOutcome.degraded === true`; assert no "recognized" verdict.
2. `live-test-timeout-not-recognized.spec.ts`: inject no signals for `tempoMaximoGestoMs`; assert `state: 'not-recognized'` and correct pt-BR message shown.
3. Use Playwright fake timers or page.clock to control timing window without real waits.

## Non-goals
- Not the test for camera permission (GEST-FE-024).

## Layer(s)
frontend / e2e

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (scenarios 3, 6)
- `docs/architecture/frontend/modules/gest/frontend/e2e/live-test-degraded-offline.feature`
- `docs/architecture/frontend/modules/gest/frontend/e2e/live-test-timeout-not-recognized.feature`

## Architecture alignment
- Playwright with `page.clock` for timing
- Tag Nx: `scope:gest`, `type:e2e`

## Implementation checklist (by layer/file)

**E2E app**
- [ ] `apps/gest-e2e/src/live-test-degraded-offline.spec.ts`
- [ ] `apps/gest-e2e/src/live-test-timeout-not-recognized.spec.ts`

**Tests**
- [ ] `npx nx e2e gest-e2e` PASS
- [ ] `npx nx lint gest-e2e` PASS

## Acceptance criteria
- Degraded: banner visible, `GestureTestOutcome.degraded === true`, no "Gesto reconhecido" shown
- Timeout: `not-recognized` verdict shown, correct pt-BR message
- `npx nx e2e gest-e2e` PASS

## Dependencies
- GEST-FE-008 (timeout mechanism)
- GEST-FE-015 (degraded banner)

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
- `page.clock.install()` + `page.clock.tick()` is the Playwright pattern for fake timers

## Prioridade
P9
