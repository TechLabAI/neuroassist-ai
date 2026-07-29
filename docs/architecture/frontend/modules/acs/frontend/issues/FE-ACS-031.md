## [FE-ACS-031] E2E Playwright — tenant-onboarding.feature (apps/acs-e2e)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
All 8 Gherkin scenarios in `e2e/tenant-onboarding.feature` are implemented as Playwright tests in
`apps/acs-e2e/src/tenant-onboarding.spec.ts`. Tests run against the `scope:web` shell page (or a test
harness) with stubs for `acs-service`. Onboarding wizard composition is tested: step 1 (provision), step 5
(primary caregiver + link), breadcrumb ownership, scope isolation.

**Context / Problem statement:**
- SPEC-08 defines 8 onboarding scenarios spanning SPEC-01, 03, 04, 06.
- These are integration tests of the wizard composition seam — the shell page composes ACS, PAC, EDGE steps.
- Scenario 7 (no ACS breadcrumb in wizard): asserts the shell breadcrumb area shows wizard trail, not ACS trail.
- Scenario 8 (no pac/edge scope import): this is a static lint check, not a runtime E2E — covered by
  `nx lint` (FE-ACS-021 boundary). In the E2E test file, replace with a runtime check that no ACS-imported
  component imports pac/edge modules.

**Approach (execution path):**
1. Create `apps/acs-e2e/src/tenant-onboarding.spec.ts` with 8 Playwright `test()` blocks.
2. Scenario 1 (201 active): stub `POST /tenants` → 201 active; assert empresa-context written (cookie/storage check).
3. Scenario 2 (202 provisioning): stub → 202; assert provisioning state shown; empresa-context NOT written.
4. Scenario 3 (retry succeeds): stub → provisioning then active on retry; assert empresa-context written.
5. Scenario 4 (still not active): stub retry → 409 tenant-not-active; assert retryable banner.
6. Scenario 5 (RBAC): stub with reduced scope; assert criar empresa disabled.
7. Scenario 6 (primary caregiver + link): stub POST caregivers + POST links → 201; assert paciente coberto.
8. Scenario 7 (no ACS breadcrumb): assert shell breadcrumb != `[Cuidadores]` while wizard active.

**Non-goals:**
- No scope isolation as runtime E2E (covered by `nx lint` boundary check).
- No PAC or EDGE step implementation (those are other modules' E2E).

**Layer(s):**
frontend / apps/acs-e2e

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (tenant-onboarding section)
- `docs/architecture/frontend/modules/acs/frontend/e2e/tenant-onboarding.feature` (all 8 scenarios)

**Architecture alignment:**
- Playwright + stubs; wizard composition tested at integration level; `nx lint` covers scope isolation
- Tags Nx: `scope:acs`, `type:e2e`

**Implementation checklist (by file):**

**App:** `apps/acs-e2e/`
- 🔲 `src/tenant-onboarding.spec.ts` — 8 test() blocks
- 🔲 `src/fixtures/tenant-stubs.ts` — stubs for POST /tenants (201, 202, 409 tenant-not-active)

**Tests**
- 🔲 `nx e2e acs-e2e --testFile tenant-onboarding.spec.ts` PASS (8 scenarios)

**Milestone:**
Wave 9 / P9 E2E + Hardening

**Labels:**
type:e2e
layer:frontend
scope:acs
priority:P9

**Acceptance criteria:**
- 8/8 scenarios PASS em CI
- Scenario 1: empresa-context escrito após 201 active
- Scenario 2: provisioning state shown; empresa-context NOT escrito
- Scenario 7: breadcrumb ACS ausente durante wizard (shell controla o trail)
- `nx lint acs-feature-caregiver-editor` 0 boundary violations (scope isolation confirmada)

**Dependencies:**
- FE-ACS-007 (AcsTenantFacade)
- FE-ACS-020 (TenantSetupComponent)
- FE-ACS-021 (CaregiverLinkStepComponent)
- INT-FE-ACS-004 (wizard composition seam)

**Data impact:**
- Nenhum (stubs)

**API / Contract impact:**
- Stubs: `POST /tenants` (201/202/409); `GET /tenants/{id}`; `POST /caregivers`; `POST /caregivers/{cid}/links`

**Testing / Evidence required:**
- CI: 8 cenários passando
- Screenshot: step 1 wizard (form empresa); step 5 wizard (form cuidador + vínculo)

**Risks / Notes:**
- O wizard shell page (`scope:web`) precisa estar disponível no E2E de onboarding. Se não estiver pronto,
  criar um test harness que importe os step components diretamente.
- O teste de empresa-context pode verificar o Angular service state via `window.__NG_APP_ID__` ou
  interceptar a request subsequente que usa o `X-Empresa-Id`.
