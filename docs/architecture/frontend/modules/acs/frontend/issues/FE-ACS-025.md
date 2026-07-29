## [FE-ACS-025] Loading skeleton states + empty states across ACS features

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
All three ACS feature surfaces (roster, editor, linkage) show a proper loading skeleton while data is in-flight
and a contextual empty state when there is no data. Loading states never show stale data rows; empty states
are distinct from error states. All states use `@neuroassist/shared/ui-feedback` tokens.

**Context / Problem statement:**
- SPEC-03 EARS: "WHILE the roster load is in flight, THE SYSTEM SHALL show a loading roster state rather than
  stale rows."
- SPEC-08 scenarios 2-3 (roster loading + error) explicitly test these states.
- Loading skeleton in roster: animating rows matching the expected `CaregiverRowComponent` shape.
- Loading in editor: disabled form skeleton while `startEdit` resolves.
- Loading in linkage: skeleton list while `loadForPatient` resolves.
- Empty roster: `RosterEmptyStateComponent` (FE-ACS-014) — already wired, this issue adds skeleton polish.

**Approach (execution path):**
1. In `CaregiverRosterComponent` template (`@switch(loadState())`):
   - `'loading'` branch: render `N` `CaregiverRowSkeleton` components (animating placeholder rows).
   - `'loaded'` with empty `roster()`: render `RosterEmptyStateComponent`.
2. In `CaregiverEditorComponent` template:
   - While `editorState() === 'editing'` and draft.nome is empty (startEdit in progress): show form skeleton.
3. In `PatientLinkageComponent` template:
   - While `mutateState() === 'loading'` (before `loadForPatient` resolves): show list skeleton.
4. Add `CaregiverRowSkeletonComponent` (presentational, `acs/ui` or inline) — pure visual placeholder.
5. Use `@neuroassist/shared/ui-feedback` pulse animation tokens for consistency.
6. Unit tests: loadState='loading' → skeleton visible (no rows); loadState='loaded' + empty → empty state.

**Non-goals:**
- No error state (FE-ACS-026 covers error UI).
- No retry logic.

**Layer(s):**
frontend / libs/acs/feature-caregiver-roster · libs/acs/feature-caregiver-editor · libs/acs/feature-patient-linkage

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§1 RosterLoadState EARS)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-08-e2e-scenarios.md` (caregiver-roster 2-3)

**Architecture alignment:**
- `@switch(loadState())` Angular control flow; `ui-feedback` tokens; no stale-data leakage
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Added/modified:**
- 🔲 `libs/acs/ui/src/lib/caregiver-row-skeleton.component.ts` + `.html` (pulse animation)
- 🔲 `libs/acs/feature-caregiver-roster/src/lib/caregiver-roster.component.html` — loading branch with skeleton rows
- 🔲 `libs/acs/feature-caregiver-editor/src/lib/caregiver-editor.component.html` — form skeleton while loading
- 🔲 `libs/acs/feature-patient-linkage/src/lib/patient-linkage.component.html` — list skeleton while loading

**Tests (additions):**
- 🔲 `caregiver-roster.component.spec.ts` — loadState='loading' → 0 real rows; N skeleton rows
- 🔲 `caregiver-roster.component.spec.ts` — loadState='loaded' + empty → RosterEmptyState visible
- 🔲 `nx test acs-feature-caregiver-roster` PASS

**Milestone:**
Wave 7 / P7 UX Polish

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P7

**Acceptance criteria:**
- `npx nx test acs-feature-caregiver-roster` PASS
- Loading: nenhuma linha de dados real visível enquanto `loadState='loading'`
- Empty: `RosterEmptyStateComponent` visível quando roster carregado mas vazio
- Skeleton: animação pulse consistente com `ui-feedback` tokens
- E2E scenario 2 (loading state) passes

**Dependencies:**
- FE-ACS-013 (roster smart, must exist)
- FE-ACS-016 (editor smart, must exist)
- FE-ACS-018 (linkage smart, must exist)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: estado de loading do roster (skeleton rows)
- Screenshot: estado empty (RosterEmptyState com CTA)

**Risks / Notes:**
- O número de skeleton rows deve ser fixo (ex: 3) ou baseado em cache de "última contagem" — escolher fixo
  para MVP (evita dependência de estado anterior).
