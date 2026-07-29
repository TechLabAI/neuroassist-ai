# GEST-FE-025 -- Playwright e2e — composition-boundary.feature + backend-error-handling.feature

**Status:** ABERTA
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens
**GitHub:** [#TBD]

## Outcome
Playwright specs for `composition-boundary.feature` (structural/composition assertions: host renders the slot, GEST has no nav item, login redirect, bootstrap init does not open camera, no breadcrumb when composed) and `backend-error-handling.feature` (ProblemDetail → correct surface per SPEC-07) pass.

## Context / Problem statement
- Scenario 7 verifies structural contracts: the shell page renders `GestureLiveTestComponent` without a direct `scope:gest` import at PAC level; GEST has no nav entry; unauthenticated access redirects; bootstrap init does NOT open camera.
- Scenario 8 verifies all 4 ProblemDetail error paths produce the correct UI surface.
- Source: SPEC-08 scenarios 7 and 8.

## Approach (execution path)
1. `composition-boundary.spec.ts`: navigate to PAC calibração page; assert `<gest-live-test>` rendered; assert no GEST nav item in shell menu; assert no breadcrumb contribution from GEST; assert no `getUserMedia` call on page load; navigate unauthenticated to `/gest` and assert login redirect.
2. `backend-error-handling.spec.ts`: mock `POST /api/v1/gest/eligibility:check` to return each ProblemDetail type; assert modal (tenant-mismatch), toast (insufficient-scope), banner (unknown-primitive, no-active-profile), etc.

## Non-goals
- ESLint nx-boundary enforcement is checked at `nx lint` time, not via Playwright (referenced for traceability in SPEC-08).

## Layer(s)
frontend / e2e

## Bounded context
gest

## Service execution spec(s)
- `docs/architecture/frontend/modules/gest/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (scenarios 7, 8)
- `docs/architecture/frontend/modules/gest/frontend/e2e/composition-boundary.feature`
- `docs/architecture/frontend/modules/gest/frontend/e2e/backend-error-handling.feature`

## Architecture alignment
- Playwright with route mocking (`page.route()`)
- Tag Nx: `scope:gest`, `type:e2e`

## Implementation checklist (by layer/file)

**E2E app**
- [ ] `apps/gest-e2e/src/composition-boundary.spec.ts`
- [ ] `apps/gest-e2e/src/backend-error-handling.spec.ts`

**Tests**
- [ ] `npx nx e2e gest-e2e` PASS
- [ ] `npx nx lint gest-e2e` PASS

## Acceptance criteria
- GEST not in shell nav menu
- `GestureLiveTestComponent` rendered inside PAC host page
- bootstrap init → no `getUserMedia` call
- Unauthenticated `/gest` → login redirect
- tenant-mismatch → modal; insufficient-scope → toast; unknown-primitive → banner
- `npx nx e2e gest-e2e` PASS

## Dependencies
- GEST-INT-001 (GestShellIntegrationService — composition works via it)
- GEST-FE-020 (error UI routing)

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
- PAC host page must exist before composition-boundary scenario can run — coordinate with PAC module timeline (GEST-INT-005)

## Prioridade
P9
