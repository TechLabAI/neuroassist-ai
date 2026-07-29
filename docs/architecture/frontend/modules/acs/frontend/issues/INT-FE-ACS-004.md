## [INT-FE-ACS-004] Onboarding wizard composition slots — ACS exposes step components without importing pac/edge scope

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
The `scope:web` shell page can import and mount `TenantSetupComponent` (step 1) and
`CaregiverLinkStepComponent` (step 5) from `@neuroassist/acs` — no circular imports. ACS NEVER imports
`scope:pac`, `scope:edge`, or `scope:gest`. The wizard shell page wires the shared session context
(`pacienteId`, active tenant) between steps via `@Input`/signal — ACS reads what the shell passes.

**Context / Problem statement:**
- SPEC-06 §2 defines the composition seam: ACS owns step sequence/descriptor; shell page imports PAC
  (PatientRegisterComponent, CommandEditorComponent) + EDGE (device-pairing) step features alongside ACS steps.
- ACS exposes only `TenantSetupComponent` + `CaregiverLinkStepComponent` — no other components cross the
  scope boundary.
- `CaregiverLinkStepComponent` receives `pacienteId` via `@Input` from the wizard shell (the shell
  resolves the active paciente via PAC, then passes the id to ACS). ACS never fetches it directly.
- Scope isolation is enforced by Nx lint boundary rules (FE-ACS-003).
- This issue verifies the seam is correct: correct exports, no circular imports, `@Input` wiring from shell.

**Approach (execution path):**
1. Verify `libs/acs/shell-entry/src/index.ts` exports `TenantSetupComponent` and `CaregiverLinkStepComponent`
   (for shell consumption).
2. Verify the shell page (test harness) can import `{ TenantSetupComponent, CaregiverLinkStepComponent } from '@neuroassist/acs'`.
3. Verify `CaregiverLinkStepComponent` has `@Input({ required: true }) pacienteId: string`.
4. Run `nx lint acs-*` — confirm 0 violations of `scope:acs cannot depend on scope:pac/edge/gest`.
5. Write a composition test (test harness): mount shell page with both ACS steps + stub PAC step; assert
   `pacienteId` flows correctly to `CaregiverLinkStepComponent`.

**Non-goals:**
- No PAC or EDGE step implementation.
- No shell page implementation (that is `scope:web`).

**Layer(s):**
frontend / libs/acs/shell-entry · libs/acs/feature-tenant-setup · libs/acs/feature-caregiver-editor

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2 composition seam)

**Architecture alignment:**
- Cross-scope composition at shell-page level only; ACS boundary enforced by lint
- Tags Nx: `scope:acs`, `type:domain`

**Implementation checklist (by file):**

**Verified (no new files — cross-cutting checks):**
- 🔲 `libs/acs/shell-entry/src/index.ts` — exports TenantSetupComponent + CaregiverLinkStepComponent
- 🔲 `libs/acs/feature-caregiver-editor/src/lib/caregiver-link-step.component.ts` — `@Input pacienteId`
- 🔲 `libs/acs/feature-tenant-setup/src/lib/tenant-setup.component.ts` — `@Output stepComplete`

**Tests**
- 🔲 Composition test harness: mount ACS steps; assert pacienteId input; assert stepComplete output
- 🔲 `nx lint acs-feature-caregiver-editor` PASS (0 scope:pac/edge imports)
- 🔲 `nx lint acs-feature-tenant-setup` PASS (0 scope:pac/edge imports)

**Milestone:**
Wave 4 / P4 Integrations

**Labels:**
type:integration
layer:frontend
scope:acs
priority:P4

**Acceptance criteria:**
- `nx lint acs-*` PASS — zero importações de scope:pac/edge/gest em qualquer lib ACS
- `@neuroassist/acs` barrel exporta TenantSetupComponent + CaregiverLinkStepComponent
- `CaregiverLinkStepComponent` recebe pacienteId via @Input (não via fetch interno)
- E2E tenant-onboarding.feature scenario 8 (scope isolation) confirmado via lint

**Dependencies:**
- FE-ACS-020 (TenantSetupComponent)
- FE-ACS-021 (CaregiverLinkStepComponent)
- FE-ACS-003 (Nx lint boundary rules)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum direto

**Testing / Evidence required:**
- `nx lint acs-feature-caregiver-editor` saída: 0 violations
- Teste de composição: pacienteId fluindo do shell para o step component

**Risks / Notes:**
- Se o shell page ainda não existir no momento desta issue, criar um TestHarness Angular que importe
  os dois step components e simule a passagem de pacienteId.
- Este é um critical boundary: qualquer importação de scope:pac em qualquer lib ACS deve falhar o lint.
