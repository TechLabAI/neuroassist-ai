## [FE-ACS-030] E2E Playwright — patient-linkage.feature (apps/acs-e2e)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
All 12 Gherkin scenarios in `e2e/patient-linkage.feature` are implemented as Playwright tests in
`apps/acs-e2e/src/patient-linkage.spec.ts`. Tests run against the `apps/acs` dev server with stubs for
`acs-service` and `pac-service`. All scenarios pass: link/unlink, last-covering guard, cross-tenant rejection,
RBAC, inactive caregiver exclusion.

**Context / Problem statement:**
- SPEC-08 defines 12 patient-linkage scenarios; they test the coverage invariant (ADR-005) from the UI.
- Key scenarios: last-covering guard blocked (5), guard bypass (6), cross-tenant rejection (7), inactive
  excluded from coverage (11).
- Stubs required: `GET /caregivers/{cid}/links` → linked caregivers; `POST /links` → 201 or 422; `DELETE /links/{id}` → 200 or 422; `GET /pac/pacientes` → patient list.

**Approach (execution path):**
1. Create `apps/acs-e2e/src/patient-linkage.spec.ts` with 12 Playwright `test()` blocks.
2. Per scenario, configure stubs for initial panel state (linked + candidate caregivers).
3. Scenario 5 (unlink last-covering blocked): stub returns coverage state where `covering=1`; assert modal.
4. Scenario 6 (bypass): configure FE to skip guard (feature flag or test helper); stub returns 422; assert modal.
5. Scenario 7 (cross-tenant): stub `POST /links` returns `422 cross-tenant-link`; assert blocking modal.
6. Scenario 11 (inactive excluded): stub returns 1 active + 1 inactive caregiver; assert coverage = 1 (coberto).

**Non-goals:**
- No roster scenarios (FE-ACS-029).
- No onboarding scenarios (FE-ACS-031).

**Layer(s):**
frontend / apps/acs-e2e

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (patient-linkage section)
- `docs/architecture/frontend/modules/acs/frontend/e2e/patient-linkage.feature` (all 12 scenarios)

**Architecture alignment:**
- Playwright + WireMock/MSW stubs; ADR-005 coverage invariant tested via E2E
- Tags Nx: `scope:acs`, `type:e2e`

**Implementation checklist (by file):**

**App:** `apps/acs-e2e/`
- 🔲 `src/patient-linkage.spec.ts` — 12 test() blocks
- 🔲 `src/fixtures/linkage-stubs.ts` — stubs for links, revoke, cross-tenant responses

**Tests**
- 🔲 `nx e2e acs-e2e --testFile patient-linkage.spec.ts` PASS (12 scenarios)

**Milestone:**
Wave 9 / P9 E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:acs
priority:P9

**Acceptance criteria:**
- 12/12 scenarios PASS em CI
- Scenario 5: modal bloqueante visível; vinculo inalterado
- Scenario 6: bypass guard → BE 422 → modal bloqueante
- Scenario 7: cross-tenant → modal bloqueante; coverage unchanged
- Scenario 11: inactive caregiver → coverage não conta

**Dependencies:**
- FE-ACS-018..027 (linkage smart + modals implementados)
- FE-ACS-029 (fixtures reutilizáveis do roster)

**Data impact:**
- Nenhum (stubs)

**API / Contract impact:**
- Stubs: shapes de CaregiverPatientLink, LinkStatus, ProblemDetail (api-contract §5 + §8)

**Testing / Evidence required:**
- CI: 12 cenários passando
- Screenshot: blocking modal cross-tenant; coverage header "Coberto (1)"

**Risks / Notes:**
- O bypass do guard (cenário 6) requer um mecanismo de teste — ex: feature flag `acs.coverageGuard=off`
  via URL query param ou ambiente de teste. Coordenar com scaffold team.
