## [PAC-FE-025] Playwright e2e — patient-register.feature (9 cenários)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/pac-e2e/src/patient-register.spec.ts` implementa os 9 cenários Gherkin de `patient-register.feature`, cobrindo: registro de paciente via onboarding wizard, RBAC (paciente write), nome obrigatório, referências inválidas (dispositivo/cuidador), ACS unreachable, missing-tenant e ausência de breadcrumb PAC no contexto de onboarding.

**Context / Problem statement:**
- 9 cenários: 1 (register desbloqueie catalog step) + 2 RBAC (write permission, nome obrigatório) + 2 referências inválidas + 1 ACS unreachable + 1 missing-tenant + 1 breadcrumb ownership.
- O e2e deve compor `PatientRegisterComponent` dentro do wizard ACS mock (já que PAC não tem rota própria para este componente).
- Cenário "contributes no PAC breadcrumb" verifica ausência de elemento de breadcrumb PAC no DOM quando montado no wizard.

**Approach (execution path):**
1. Criar `apps/pac-e2e/src/patient-register.spec.ts`.
2. Implementar `OnboardingWizardPage` page object (stub): `gotoStep2Paciente()`, `fillNome(text)`, `getSaveButton()`, `getBreadcrumb()`.
3. Usar `page.route` para mock de `POST /api/pac/v1/pacientes` (201 success, validation 400, ACS 503).
4. Testar 9 cenários conforme o feature file.

**Non-goals:**
- Sem teste do wizard ACS real — apenas o step 2 "Paciente" em isolamento.

**Layer(s):**
frontend / apps/pac-e2e

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/e2e/patient-register.feature`

**Architecture alignment:**
- Playwright Test runner
- Tag Nx: `scope:pac`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/pac-e2e**
- [ ] `apps/pac-e2e/src/patient-register.spec.ts`
- [ ] `apps/pac-e2e/src/page-objects/onboarding-wizard.page.ts`

**Tests**
- [ ] `npx nx e2e pac-e2e --testFile=patient-register.spec.ts` PASS (9/9 scenarios)

**Milestone:**
Wave 9 / P9 e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:pac
priority:P9

**Acceptance criteria:**
- `npx nx e2e pac-e2e --testFile=patient-register.spec.ts` PASS: 9/9 cenários GREEN.
- Cenário "Patient register composed inside onboarding → no PAC breadcrumb" → PASS.
- Cenário "Unknown dispositivo reference → inline validation error" → PASS.

**Dependencies:**
- PAC-FE-016 (PatientRegisterComponent implementado)
- INT-FE-PAC-005 (ACS/EDGE cross-module pickers)

**Data impact:**
Nenhum (mocks).

**API / Contract impact:**
- Moca `POST /api/pac/v1/pacientes` (success + 3 error cases).

**Testing / Evidence required:**
- CI report com 9/9 green.

**Risks / Notes:**
- O contexto do wizard ACS é externo ao módulo PAC; o e2e precisa de uma harness mínima de wizard para compor o `PatientRegisterComponent`. Criar um harness stub no `pac-e2e` que simula o wrapper do wizard.
