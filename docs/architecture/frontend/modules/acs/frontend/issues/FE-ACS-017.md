## [FE-ACS-017] Editor presentational sub-components (CaregiverForm + field components + SaveStatus + DeactivateAction)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Seven pure presentational components for the caregiver editor: `CaregiverFormComponent` (form container),
four field components (NomeField, LoginField, RoleSelector, SenhaField), `SaveStatusComponent`, and
`DeactivateActionComponent`. All standalone, OnPush, no facade access.

**Context / Problem statement:**
- SPEC-02 §2 defines the component tree under `CaregiverEditorComponent`.
- `NomeFieldComponent` and `LoginFieldComponent` run synchronous required validators.
- `SenhaFieldComponent` is write-only — rendered only on create mode (parent controls visibility).
- `DeactivateActionComponent` receives `isLastCovering: boolean`; disables button and surfaces blocking reason
  when `true` (ADR-005).
- `SaveStatusComponent` receives `editorState: EditorState` and emits `(retry)` when in `'error'` state.
- Bloqueia: FE-ACS-016 (editor smart component renders these)

**Approach (execution path):**
1. Create `libs/acs/feature-caregiver-editor/src/lib/components/`:
   - `caregiver-form.component.ts`: `@Input() draft: CuidadorFormVM`; `@Output() change: Partial<CuidadorFormVM>`.
   - `nome-field.component.ts`: `@Input() value: string`; `@Output() change: string`; required validator.
   - `login-field.component.ts`: `@Input() value: string`; `@Output() change: string`; required validator.
   - `role-selector.component.ts`: `@Input() papel`; `@Output() change`; fixed values (cuidadora / coordenador_clinica).
   - `senha-field.component.ts`: `@Input() value: string`; `@Output() change: string`; write-only (no display).
   - `save-status.component.ts`: `@Input() editorState: EditorState`; `@Output() retry`.
   - `deactivate-action.component.ts`: `@Input() status`; `@Input() isLastCovering: boolean`; `@Output() deactivate`.
2. All field components: standalone, OnPush, synchronous validators via `Validators.required`.
3. `DeactivateAction`: disabled + blocking message when `isLastCovering === true`.
4. Unit tests: validators fire; deactivate disabled; senha not echoed (no display-value output).

**Non-goals:**
- No facade access in any presentational component.
- No async validators (BE 409 is handled at the editor smart level via SPEC-07).

**Layer(s):**
frontend / libs/acs/feature-caregiver-editor

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§2 CuidadorFormVM, EditorState)

**Architecture alignment:**
- Standalone, OnPush; @Input/@Output only; Validators.required synchronous
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/feature-caregiver-editor/src/lib/components/`
- 🔲 `caregiver-form.component.ts` + `.html`
- 🔲 `nome-field.component.ts` + `.html` (required validator)
- 🔲 `login-field.component.ts` + `.html` (required validator)
- 🔲 `role-selector.component.ts` + `.html` (cuidadora | coordenador_clinica)
- 🔲 `senha-field.component.ts` + `.html` (write-only; type=password; never echoed)
- 🔲 `save-status.component.ts` + `.html` (editorState enum → message + retry button)
- 🔲 `deactivate-action.component.ts` + `.html` (disabled + reason when isLastCovering)

**Tests**
- 🔲 `*.component.spec.ts` — validators; deactivate disabled; senha write-only; save-status retry
- 🔲 `nx test acs-feature-caregiver-editor` PASS
- 🔲 `nx lint acs-feature-caregiver-editor` PASS

**Milestone:**
Wave 3 / P3 Feature libs

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P3

**Acceptance criteria:**
- `npx nx test acs-feature-caregiver-editor` PASS
- `npx nx lint acs-feature-caregiver-editor` PASS
- Nome/Login em branco → salvar desabilitado (validators.required)
- `SenhaField` não expõe a senha via `@Output()` como valor de volta; type=password
- `DeactivateAction(isLastCovering=true)` → botão desabilitado + blocking reason mostrado
- `SaveStatus(editorState='error')` → botão retry visível

**Dependencies:**
- FE-ACS-003 (lib skeleton)
- FE-ACS-010 (CuidadorFormVM type)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (presentational only)

**Testing / Evidence required:**
- Screenshots: form em create (senha visível) e edit (senha ausente)
- Screenshot: deactivate disabled + reason

**Risks / Notes:**
- O campo senha write-only significa que o `value @Input` deve aceitar apenas `''` no edit mode (nunca
  pré-preenchido). O parent (`CaregiverEditorComponent`) controla a visibilidade do campo.
