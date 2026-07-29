## [FE-ACS-029] E2E Playwright — caregiver-roster.feature (apps/acs-e2e)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
All 20 Gherkin scenarios in `e2e/caregiver-roster.feature` are implemented as Playwright tests in
`apps/acs-e2e`. Tests run against the `apps/acs` dev server with WireMock stubs for `acs-service`.
All scenarios pass: roster render, loading/error states, coverage honesty, invite/deactivate guards,
RBAC gating, bootstrap lifecycle.

**Context / Problem statement:**
- SPEC-08 defines the scenario inventory; `caregiver-roster.feature` covers 20 scenarios.
- WireMock stubs simulate: roster list, invite POST, 409 login-duplicate, deactivate POST, 404 not-found,
  and guard bypass BE enforcement (409 on deactivate).
- Coverage scenarios (sem-cobertura, isLastCovering) require stubs that return specific link + caregiver states.
- Bootstrap scenarios (init/destroy) are integration-level and may use Angular TestBed + Playwright combined.

**Approach (execution path):**
1. Map each Gherkin scenario to a Playwright `test()` in `apps/acs-e2e/src/caregiver-roster.spec.ts`.
2. Set up WireMock/MSW stubs matching BE API contract shapes per scenario.
3. For coverage scenarios: stub returns 1 active + 1 inactive caregiver linked to a paciente.
4. Assert DOM state for each scenario (coverage badge, loading skeleton, blocking modal, redirect).
5. Run `nx e2e acs-e2e --testFile caregiver-roster.spec.ts` and verify all 20 pass.
6. Screenshots captured on failure for CI reporting.

**Non-goals:**
- No real backend calls (all stubs).
- No patient-linkage scenarios (FE-ACS-030).

**Layer(s):**
frontend / apps/acs-e2e

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (caregiver-roster section)
- `docs/architecture/frontend/modules/acs/frontend/e2e/caregiver-roster.feature` (all 20 scenarios)

**Architecture alignment:**
- Playwright + `apps/acs-e2e`; WireMock/MSW stubs; screenshots on failure
- Tags Nx: `scope:acs`, `type:e2e`

**Implementation checklist (by file):**

**App:** `apps/acs-e2e/`
- 🔲 `src/caregiver-roster.spec.ts` — 20 test() blocks, one per Gherkin scenario
- 🔲 `src/fixtures/roster-stubs.ts` — WireMock/MSW stub definitions for caregiver roster
- 🔲 `src/fixtures/coverage-stubs.ts` — stubs for coverage scenarios (active/inactive caregivers)
- 🔲 `playwright.config.ts` — baseURL pointing to apps/acs dev server

**Tests**
- 🔲 `nx e2e acs-e2e --testFile caregiver-roster.spec.ts` PASS (20 scenarios)

**Milestone:**
Wave 9 / P9 E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:acs
priority:P9

**Acceptance criteria:**
- 20/20 scenarios PASS in CI
- `nx e2e acs-e2e` PASS (0 failures)
- Screenshots captured on any failure
- Coverage scenarios: inactive caregiver excluded from coverage count (scenario 5)
- Guard bypass: blocking modal shown (scenarios 12, 16)

**Dependencies:**
- FE-ACS-013..027 (todos os componentes e facades devem estar implementados)
- FE-ACS-003 (apps/acs-e2e skeleton deve existir)

**Data impact:**
- Nenhum (stubs only)

**API / Contract impact:**
- Stubs devem corresponder ao `api-contract.md` shapes (200 /caregivers, 409 error format)

**Testing / Evidence required:**
- CI output: 20 cenários passando
- Screenshot: coverage badge, blocking modal, loading skeleton

**Risks / Notes:**
- Bootstrap lifecycle scenarios (init/destroy) podem ser difíceis de testar via Playwright puro —
  considerar Angular integration tests para esses dois cenários específicos.
- WireMock stubs de timing (slow request para "loading state") requerem delay configurável no mock.
