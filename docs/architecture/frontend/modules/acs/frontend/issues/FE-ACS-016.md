## [FE-ACS-016] CaregiverEditorComponent (smart — EditorState + draft signals + save/deactivate)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`CaregiverEditorComponent` (standalone, OnPush) serves both create (`/acs/cuidadores/novo`) and edit
(`/acs/cuidadores/:cuidadorId/editar`) modes, switching via `:cuidadorId` param presence. It reads three
signals from `AcsCuidadorFacade` (editorState, draft, saveError), dispatches save/deactivate/cancel, and
never echoes a loaded caregiver's senha.

**Context / Problem statement:**
- SPEC-02 §2 defines the full editor component tree; mode selection is driven by route param, not two components.
- SPEC-03 §2 defines `CaregiverEditorVM`, `EditorState`, and `CuidadorFormVM`.
- `senha` is write-only: the field renders for create-mode; on edit-mode the field is absent (BE never returns it).
- `deactivate` is disabled when `isLastCovering === true` (ADR-005, SPEC-02 §2, SPEC-03 §2).
- `canSave` derived: form valid (nome+login+papel+senha-on-create).
- Bloqueia: FE-ACS-017 (presentational form components rendered here)

**Approach (execution path):**
1. Create `libs/acs/feature-caregiver-editor/src/lib/caregiver-editor.component.ts` (standalone, OnPush).
2. Inject `AcsCuidadorFacade`, `ActivatedRoute`, `Router`.
3. On init: read `:cuidadorId` from route; if present → `facade.startEdit(id)`; else → `facade.startNew()`.
4. Bind template to: `editorState()`, `draft()`, `saveError()` (facade signals).
5. `(change)` from `CaregiverFormComponent` → `facade.patchDraft(partial)`.
6. `(save)` → `facade.saveCaregiver(draft())`.
7. `(deactivate)` → `facade.deactivate(id)` (button disabled if `editorVM().isLastCovering`).
8. `(cancel)` → Router.navigate back to roster.
9. Redirect to roster if `404 caregiver-not-found` (SPEC-07).
10. Unit tests: create vs edit mode; senha absent in edit; deactivate disabled when isLastCovering; redirect on 404.

**Non-goals:**
- No presentational form fields (FE-ACS-017).
- No linkage logic.

**Layer(s):**
frontend / libs/acs/feature-caregiver-editor

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§2 CaregiverEditorVM)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-07-error-handling.md` (caregiver-not-found redirect)

**Architecture alignment:**
- Standalone, OnPush, Signals; `:cuidadorId` presence = create vs edit
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/feature-caregiver-editor/`
- 🔲 `src/lib/caregiver-editor.component.ts` — smart, mode-select, signals binding
- 🔲 `src/lib/caregiver-editor.component.html` — template with mode conditional (senha field shown only on create)
- 🔲 `src/index.ts` — re-exports component

**Tests**
- 🔲 `src/lib/caregiver-editor.component.spec.ts`
  - Create mode: senha field visible; startNew() called
  - Edit mode: pre-filled; senha field absent; startEdit(id) called
  - Deactivate disabled when isLastCovering
  - 404 → redirect to roster not-found view
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
- `npx nx build acs-feature-caregiver-editor` PASS
- Edit mode: senha nunca renderizada; caregiver pre-filled
- Deactivate button disabled quando `isLastCovering=true`
- 404 de caregiver-not-found → redirect para roster not-found view
- Cobertura visual: SPEC-02 §2 ASCII tree replicado em DOM

**Dependencies:**
- FE-ACS-010 (AcsCuidadorFacade)
- FE-ACS-017 (CaregiverFormComponent + field components)
- FE-ACS-027 (blocking modal para deactivate last-covering com bypass)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Orquestra via facade: `POST /caregivers` (create) | `GET /caregivers/{id}` (edit) | `POST …/deactivation`

**Testing / Evidence required:**
- Screenshot: create mode (com campo senha); edit mode (sem campo senha, pré-preenchido)
- Screenshot: deactivate button disabled com tooltip/reason

**Risks / Notes:**
- O `facade.startEdit(id)` deve carregar o cuidador e pré-preencher o draft — se o cuidador não existir,
  o BE retorna 404 e o FE deve redirecionar (não mostrar editor em branco).
