## [FE-EDGE-026] Playwright e2e — backend-error-handling.feature + a11y + perf budget (SPEC-08 #8)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/edge-e2e/src/backend-error-handling.spec.ts` asserts correct error surface routing for
`ProblemDetail` responses: validation banner, insufficient-scope toast, pairing-conflict banner,
device-retired banner. No credential/biometric appears in any error message. Accessibility and
performance budget gates are included.

**Context / Problem statement:**
- SPEC-08 #8: `ProblemDetail.type` → correct surface (banner/toast/modal). This ensures the error
  mapping (INT-FE-EDGE-006) is wired end-to-end, not just unit-tested in shared/error-model.
- Privacy invariant: no `enrollmentToken`, `clientSecret`, video, or landmark in any DOM error element
  (api-contract §8, SPEC-07 §9).
- Accessibility: axe-core scan on the error surfaces. No critical/serious violations.
- Performance budget: `edge` build bundle should not exceed a project-agreed budget (tracked here as
  gate; actual budget value defined in `apps/edge/project.json` `budgets`).

**Approach (execution path):**
1. Stub `POST /devices` → `400 /errors/validation` → assert inline banner "Verifique os dados..."
2. Stub `POST .../pairings` → `403 /errors/insufficient-scope` → assert toast (not banner/modal).
3. Stub `DELETE .../pairings/current` → `409 /errors/pairing-conflict` → assert banner "Este paciente
   já tem um dispositivo ativo..."
4. Stub `GET /devices/{id}` → `409 /errors/device-retired` → assert banner "Este dispositivo foi
   aposentado..."
5. For each error surface: assert NO `clientSecret`, `enrollmentToken`, or biometric text in DOM.
6. Run axe-core accessibility scan on error state pages; assert zero critical violations.
7. Confirm build bundle: `nx build edge --configuration=production` budget check.

**Non-goals:**
- Does not test connectivity poll errors (FE-EDGE-023 covers `unknown` state).
- Does not test all 12 error types from SPEC-07 (unit tests cover the rest).

**Layer(s):**
frontend / apps/edge-e2e

**Bounded context:**
edge

**Service execution spec(s):**
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (§8)
- `docs/architecture/frontend/modules/edge/frontend/frontend-spec/SPEC-07-error-handling.md` (§1, §4)
- `docs/architecture/frontend/modules/edge/frontend/e2e/backend-error-handling.feature`

**Architecture alignment:**
- Tag Nx: `scope:edge`, `type:e2e`
- WCAG 2.1 AA target for error states

**Implementation checklist (by file):**

**E2E paths**
- 🔲 `apps/edge-e2e/src/backend-error-handling.spec.ts`
- 🔲 `apps/edge-e2e/src/a11y/edge-error-a11y.spec.ts` (axe-core scan)

**Tests**
- 🔲 `nx e2e edge-e2e --spec=backend-error-handling` PASS
- 🔲 `nx e2e edge-e2e --spec=edge-error-a11y` PASS (zero axe critical violations)
- 🔲 `nx build edge --configuration=production` PASS (within bundle budget)

**Milestone:**
Wave P9 / E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:edge
priority:P9

**Acceptance criteria:**
- All backend-error-handling scenarios PASS
- Privacy: DOM scan finds no `clientSecret`, `enrollmentToken`, or biometric data in error messages
- axe-core: zero critical + serious violations on error surfaces
- Bundle budget: production build within agreed limits

**Dependencies:**
- FE-EDGE-018 (loading/empty states — error banner rendering)
- INT-FE-EDGE-006 (shared/error-model EDGE pt-BR copy — must be wired before this e2e runs)
- FE-EDGE-021 (shared stubs infrastructure)

**Data impact:**
- Nenhum (stubbed)

**API / Contract impact:**
- Stubs: all 4 ProblemDetail types listed above

**Testing / Evidence required:**
- Playwright report: 4 error scenarios green
- Privacy assertion: `page.content()` does not contain 'clientSecret' or 'enrollmentToken'
- axe-core report: zero critical violations

**Risks / Notes:**
- Bundle budget value must be agreed with the team before this issue ships; a placeholder budget
  (500 kB initial chunk) is set in `apps/edge/project.json` — adjust per project guidelines.
- axe-core integration in Playwright: use `@axe-core/playwright` package (confirm it is in devDependencies).
