## [FE-ACS-019] Linkage presentational components (PatientCoverageHeader, LinkedCaregiverList+Row, UnlinkedCandidateList, LinkageErrorCard)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Five pure presentational components for the patient linkage panel. All standalone, OnPush, no facade access.
`PatientCoverageHeaderComponent` shows "Coberto (n)" or "Sem cobertura" danger always.
`LinkedCaregiverRowComponent` has `unlink` disabled when `row.isLastCovering === true` with blocking reason.

**Context / Problem statement:**
- SPEC-02 §3 defines the full sub-component tree for `PatientLinkageComponent`.
- `PatientCoverageHeaderComponent`: `@Input() coverage: PatientCoverageVM` — danger state is always surfaced,
  never hidden (ADR-005).
- `LinkedCaregiverRowComponent`: `@Input() row: VinculoRowVM`; unlink disabled when `isLastCovering`.
- `UnlinkedCandidateListComponent`: `@Input() candidates: CuidadorRowVM[]`; `@Output() link: string` (cuidadorId).
- `LinkageErrorCardComponent`: `@Input() error: AppError`; `@Output() retry`.
- Bloqueia: FE-ACS-018 (smart component renders these)

**Approach (execution path):**
1. Create `libs/acs/feature-patient-linkage/src/lib/components/`:
   - `patient-coverage-header.component.ts` — renders coberto/sem-cobertura state from `PatientCoverageVM`.
   - `linked-caregiver-list.component.ts` — list container (renders `linked-caregiver-row` per item).
   - `linked-caregiver-row.component.ts` — `@Input() row: VinculoRowVM`; unlink disabled on `isLastCovering`.
   - `unlinked-candidate-list.component.ts` — `@Input() candidates`; `@Output() link`.
   - `linkage-error-card.component.ts` — `@Input() error`; `@Output() retry`.
2. All: standalone, OnPush.
3. Unit tests: coverage header danger state; unlink disabled on isLastCovering; candidates link output.

**Non-goals:**
- No facade access.
- No routing.

**Layer(s):**
frontend / libs/acs/feature-patient-linkage

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§3)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§3)

**Architecture alignment:**
- Standalone, OnPush; @Input/@Output; icon + label for coverage state
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/feature-patient-linkage/src/lib/components/`
- 🔲 `patient-coverage-header.component.ts` + `.html`
- 🔲 `linked-caregiver-list.component.ts` + `.html`
- 🔲 `linked-caregiver-row.component.ts` + `.html` (unlink disabled on isLastCovering)
- 🔲 `unlinked-candidate-list.component.ts` + `.html`
- 🔲 `linkage-error-card.component.ts` + `.html`

**Tests**
- 🔲 `*.component.spec.ts` — coverage header danger; unlink disabled; candidates output; error retry
- 🔲 `nx test acs-feature-patient-linkage` PASS
- 🔲 `nx lint acs-feature-patient-linkage` PASS

**Milestone:**
Wave 3 / P3 Feature libs

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P3

**Acceptance criteria:**
- `npx nx test acs-feature-patient-linkage` PASS
- `npx nx lint acs-feature-patient-linkage` PASS
- `PatientCoverageHeader(coverage.state='sem-cobertura')` → danger visível
- `LinkedCaregiverRow(row.isLastCovering=true)` → botão unlink desabilitado + reason
- `UnlinkedCandidateList` emite `(link)` com cuidadorId correto

**Dependencies:**
- FE-ACS-003 (lib skeleton)
- FE-ACS-011 (VinculoRowVM type)
- FE-ACS-012 (PatientCoverageVM type)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshots: coberto header; sem-cobertura header (danger); unlink disabled

**Risks / Notes:**
- `LinkedCaregiverRowComponent` precisa mostrar o `papel` do cuidador (para reuso dos badges de `acs/ui`).
  Verificar se o `VinculoRowVM` inclui `papel` (SPEC-03 §3: inclui — verificado).
