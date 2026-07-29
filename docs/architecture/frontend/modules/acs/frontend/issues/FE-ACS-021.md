## [FE-ACS-021] CaregiverLinkStepComponent (smart — onboarding step 5)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`CaregiverLinkStepComponent` (standalone, OnPush) — the ACS onboarding wizard step 5 ("Cuidador e vínculo").
Composed inside the shell page (not routed). Receives the active tenant and `pacienteId` via `@Input`/signal
from the wizard shell, registers the primary caregiver, and links it so the patient starts covered from day one.
Reuses `CaregiverFormComponent` from `feature-caregiver-editor`.

**Context / Problem statement:**
- SPEC-02 §2 defines `CaregiverLinkStepComponent` as part of `feature-caregiver-editor` barrel.
- SPEC-06 §2 defines the composition seam: shell wires `pacienteId` via `@Input`/signal; ACS NEVER imports
  `scope:pac`/`scope:edge`/`scope:gest`.
- The step registers a primary caregiver (`AcsCuidadorFacade.saveCaregiver`) then links it
  (`AcsLinkFacade.link(cid, pid)`), making the paciente "coberto" from day one.
- No breadcrumb contribution (wizard owns the trail).
- `@Output() stepComplete` emitted when caregiver is registered + linked.

**Approach (execution path):**
1. Create `libs/acs/feature-caregiver-editor/src/lib/caregiver-link-step.component.ts` (standalone, OnPush).
2. `@Input({ required: true }) pacienteId: string` — received from wizard shell (never fetched by ACS).
3. Inject `AcsCuidadorFacade`, `AcsLinkFacade`.
4. Reuse `CaregiverFormComponent` for caregiver data entry.
5. On submit: call `facade.saveCaregiver(draft)` → on saved get new cuidadorId → call
   `acsLinkFacade.link(cid, pacienteId)` → emit `stepComplete`.
6. No breadcrumb (call `acsNavService.clearBreadcrumb()` in init).
7. Unit tests: saveCaregiver + link called in sequence; stepComplete emitted; no pac/edge import.

**Non-goals:**
- No PAC patient creation (shell handles PAC steps independently).
- No routing.
- No EDGE imports.

**Layer(s):**
frontend / libs/acs/feature-caregiver-editor

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2 CaregiverLinkStepComponent)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2 composition seam)

**Architecture alignment:**
- Standalone, OnPush; @Input for pacienteId; no scope:pac/edge/gest import
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/feature-caregiver-editor/`
- 🔲 `src/lib/caregiver-link-step.component.ts` — step 5: register + link; stepComplete output
- 🔲 `src/lib/caregiver-link-step.component.html`
- 🔲 `src/index.ts` — re-exports CaregiverLinkStepComponent

**Tests**
- 🔲 `src/lib/caregiver-link-step.component.spec.ts` — save + link sequence; stepComplete; no pac/edge import
- 🔲 `nx test acs-feature-caregiver-editor` PASS
- 🔲 `nx lint acs-feature-caregiver-editor` PASS (no cross-scope imports)

**Milestone:**
Wave 3 / P3 Feature libs

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P3

**Acceptance criteria:**
- `npx nx test acs-feature-caregiver-editor` PASS
- `npx nx lint acs-feature-caregiver-editor` PASS (0 boundary violations)
- `saveCaregiver` + `link(cid, pacienteId)` chamados em sequência
- `stepComplete` emitido após vínculo criado
- `nx lint`: 0 imports de `scope:pac`, `scope:edge`, `scope:gest`
- E2E tenant-onboarding.feature scenario 6 (primary caregiver + link) suportado

**Dependencies:**
- FE-ACS-010 (AcsCuidadorFacade)
- FE-ACS-011 (AcsLinkFacade)
- FE-ACS-017 (CaregiverFormComponent reutilizado)
- FE-ACS-006 (clearBreadcrumb)

**Data impact:**
- Nenhum persistido no FE (cria cuidador e vínculo via BE)

**API / Contract impact:**
- Via façades: `POST /caregivers` + `POST /caregivers/{cid}/links`

**Testing / Evidence required:**
- Screenshot: step 5 do wizard (form de cuidador + botão vincular)
- Lint: 0 boundary violations para scope:acs imports de scope:pac/edge

**Risks / Notes:**
- O `pacienteId` é passado pelo shell (não buscado pelo ACS) — ACS nunca importa PAC.
  A forma como o shell resolve o `pacienteId` ativo é responsabilidade do `scope:web`.
