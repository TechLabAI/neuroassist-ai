## [FE-ACS-024] Dynamic breadcrumb enrichment (cuidador name + paciente name in breadcrumb signal)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Smart components (`CaregiverEditorComponent`, `PatientLinkageComponent`) update the `AcsNavService` breadcrumb
signal with the fetched entity name, producing dynamic labels like `Editar "Jamilly Amaral"` and
`Vínculos de "Márcio Amaral"`. The roster route uses the static `[Cuidadores]` label. Wizard steps contribute
no breadcrumb (cleared on init of composed steps).

**Context / Problem statement:**
- SPEC-01 §1 breadcrumb column defines interpolated labels for editor-edit and linkage routes.
- FE-ACS-006 provides `AcsNavService.setBreadcrumb(route, params)` — this issue wires smart components to
  call it with the right params after the entity is loaded.
- `CaregiverEditorComponent` calls `setBreadcrumb('editor-edit', {nome: cuidador.nome})` after `startEdit(id)`.
- `PatientLinkageComponent` calls `setBreadcrumb('linkage', {paciente: paciente.nome})` after `loadForPatient`.
- `TenantSetupComponent` and `CaregiverLinkStepComponent` call `clearBreadcrumb()` on init.

**Approach (execution path):**
1. In `CaregiverEditorComponent` (FE-ACS-016): after `startEdit(id)` resolves and draft is loaded, call
   `acsNavService.setBreadcrumb('editor-edit', {nome: draft().nome})`.
2. In `PatientLinkageComponent` (FE-ACS-018): after `loadForPatient(pid)` resolves, call
   `acsNavService.setBreadcrumb('linkage', {paciente: pacienteNome})`.
3. Roster route: call `setBreadcrumb('roster')` in `CaregiverRosterComponent.ngOnInit`.
4. Verify wizard components call `clearBreadcrumb()` on init (FE-ACS-020, FE-ACS-021).
5. Unit tests: editor-edit → breadcrumb has interpolated name; linkage → paciente name; wizard → clear.

**Non-goals:**
- No breadcrumb rendering (shell owns the UI).
- No router.events subscription.

**Layer(s):**
frontend / libs/acs/feature-caregiver-editor · libs/acs/feature-patient-linkage · libs/acs/feature-caregiver-roster

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-01-route-map.md` (§1 breadcrumb column)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

**Architecture alignment:**
- Smart components call `AcsNavService` — no reactive chain needed; just a call after signal resolves
- Tags Nx: `scope:acs`, `type:feature` (touched in existing components — no new file)

**Implementation checklist (by file):**

**Touched components (additions, not new files):**
- 🔲 `libs/acs/feature-caregiver-editor/src/lib/caregiver-editor.component.ts` — setBreadcrumb('editor-edit', {nome})
- 🔲 `libs/acs/feature-patient-linkage/src/lib/patient-linkage.component.ts` — setBreadcrumb('linkage', {paciente})
- 🔲 `libs/acs/feature-caregiver-roster/src/lib/caregiver-roster.component.ts` — setBreadcrumb('roster')
- 🔲 `libs/acs/feature-tenant-setup/src/lib/tenant-setup.component.ts` — clearBreadcrumb() on init
- 🔲 `libs/acs/feature-caregiver-editor/src/lib/caregiver-link-step.component.ts` — clearBreadcrumb() on init

**Tests (additions to existing specs):**
- 🔲 `caregiver-editor.component.spec.ts` — setBreadcrumb called with cuidador.nome after startEdit
- 🔲 `patient-linkage.component.spec.ts` — setBreadcrumb called with paciente.nome
- 🔲 `tenant-setup.component.spec.ts` — clearBreadcrumb called; no ACS breadcrumb contributed
- 🔲 `nx test acs-feature-*` PASS

**Milestone:**
Wave 6 / P6 Navigation

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P6

**Acceptance criteria:**
- `npx nx test acs-feature-caregiver-editor` PASS
- `npx nx test acs-feature-patient-linkage` PASS
- breadcrumb signal: edit route → `[{label:'Cuidadores'}, {label:'Editar "Jamilly Amaral"'}]`
- breadcrumb signal: linkage route → `[{label:'Cuidadores'}, {label:'Vínculos de "Márcio Amaral"'}]`
- Wizard steps: breadcrumb signal = `[]` while composed

**Dependencies:**
- FE-ACS-006 (AcsNavService with setBreadcrumb/clearBreadcrumb)
- FE-ACS-016 (editor smart, must exist)
- FE-ACS-018 (linkage smart, must exist)
- FE-ACS-020 (tenant setup, must exist)
- FE-ACS-021 (caregiver link step, must exist)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum direto; o nome vem do sinal de draft/paciente já carregado

**Testing / Evidence required:**
- Screenshot do header do browser com breadcrumb dinâmico no editor
- E2E: wizard steps — sem breadcrumb ACS (tenant-onboarding.feature 7)

**Risks / Notes:**
- Se o nome ainda não estiver disponível quando o componente faz `setBreadcrumb`, o breadcrumb pode
  inicialmente mostrar apenas o label estático e depois atualizar. Usar `effect()` para reagir ao signal.
