# GEST-FE-024 -- Playwright e2e — camera-permission-error.feature + camera-lifecycle-teardown.feature

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
Playwright specs for `camera-permission-error.feature` (camera denied → `camera-error` state + recovery/retry) and `camera-lifecycle-teardown.feature` (zero MediaStream leaks on stop / navigate-away / destroy) pass in `apps/gest-e2e`.

## Context / Problem statement
- Scenario 4: `getUserMedia` denied (Playwright can set camera permission to denied) → `camera-error` state → first-class error panel shown.
- Scenario 5 (critical): asserts, after each exit path, that `navigator.mediaDevices` tracks are all `readyState === 'ended'` (zero-leak success signal, SPEC-02 §1, doc 01 §7).
- Source: SPEC-08 scenarios 4 and 5.

## Approach (execution path)
1. `camera-permission-error.spec.ts`: use Playwright `context.grantPermissions([], { origin })` to deny camera; start test; assert `camera-error` panel visible with "Tentar de novo"; click retry; assert state resets.
2. `camera-lifecycle-teardown.spec.ts`: start a test (camera acquired); stop test; evaluate all `MediaStreamTrack.readyState === 'ended'`; repeat for navigate-away and component destroy scenarios.
3. Use `page.evaluate()` to check `navigator.mediaDevices.enumerateDevices()` and track states.

## Non-goals
- Not testing bridge unreachable (GEST-FE-023).

## Layer(s)
frontend / e2e

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (scenarios 4, 5)
- `docs/architecture/frontend/modules/gest/frontend/e2e/camera-permission-error.feature`
- `docs/architecture/frontend/modules/gest/frontend/e2e/camera-lifecycle-teardown.feature`

## Architecture alignment
- Playwright with permission control API
- Tag Nx: `scope:gest`, `type:e2e`

## Implementation checklist (by layer/file)

**E2E app**
- [ ] `apps/gest-e2e/src/camera-permission-error.spec.ts`
- [ ] `apps/gest-e2e/src/camera-lifecycle-teardown.spec.ts`

**Tests**
- [ ] `npx nx e2e gest-e2e` PASS
- [ ] `npx nx lint gest-e2e` PASS

## Acceptance criteria
- Camera denied → first-class error panel with "Tentar de novo" visible
- After stop/navigate/destroy → ALL MediaStreamTrack `readyState === 'ended'`
- `npx nx e2e gest-e2e` PASS

## Dependencies
- GEST-FE-008 (teardown in facade)
- GEST-FE-019 (camera-error panel)

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
- MediaStream teardown test is the single most important invariant in this module — LGPD privacy-by-default requires NO camera activity outside an active test

## Prioridade
P9
