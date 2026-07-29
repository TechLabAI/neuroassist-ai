## [FE-ACS-013] CaregiverRosterComponent (smart — roster + KPI + coverage alert wiring)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`CaregiverRosterComponent` (standalone, OnPush) is the smart component for `/acs`. It reads five signals from
`AcsCuidadorFacade` and `CoverageFacade`, dispatches actions (loadRoster, invite, edit, deactivate,
openLinkage), and conditionally renders the correct sub-component based on `loadState`. Coverage alert bar is
always visible when `pacientesSemCobertura > 0` — never hidden.

**Context / Problem statement:**
- SPEC-02 §1 defines the full component tree; the smart component wires signals and dispatches events.
- The roster must show loading state while fetching, error card on failure, and empty state if no caregivers.
- `CoverageAlertBarComponent` must be visible (danger) whenever any paciente has zero active coverage (ADR-005).
- `deactivate(id)` is disabled on the row if `isLastCovering === true` (guard runs before calling the facade).
- Bloqueia: FE-ACS-014 (presentational sub-components rendered here), FE-ACS-015 (CaregiverRowComponent)

**Approach (execution path):**
1. Create `libs/acs/feature-caregiver-roster/src/lib/caregiver-roster.component.ts` (standalone, OnPush).
2. Inject `AcsCuidadorFacade`, `CoverageFacade`, `Router`.
3. Bind signals: `roster`, `loadState`, `coverageByPatient` (from CoverageFacade).
4. Call `loadRoster()` in `ngOnInit` if `loadState() === 'idle'`.
5. Template: `@switch(loadState())` for loading/error/loaded/empty states.
6. Pass `row` signals to `CaregiverRowComponent`; bind `(edit)`, `(deactivate)`, `(manageLinks)` outputs.
7. Always show `CoverageAlertBarComponent` when `kpis().pacientesSemCobertura > 0` (no hide logic).
8. Unit test: loads on init; delegates `deactivate` to facade; coverage alert shown for sem-cobertura;
   error card with retry shown on failure.

**Non-goals:**
- No CRUD form logic (FE-ACS-016 — CaregiverEditorComponent).
- No patient linkage logic (FE-ACS-018 — PatientLinkageComponent).

**Layer(s):**
frontend / libs/acs/feature-caregiver-roster

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§1 RosterKpiVM)

**Architecture alignment:**
- Standalone, OnPush, Signals; no `BehaviorSubject` in component
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/feature-caregiver-roster/`
- 🔲 `src/lib/caregiver-roster.component.ts` — smart, reads 3 signals, dispatches 5 actions
- 🔲 `src/lib/caregiver-roster.component.html` — template with @switch, coverage alert, row list
- 🔲 `src/lib/caregiver-roster.routes.ts` — exports CAREGIVER_ROSTER_ROUTES (re-used by acs.routes)
- 🔲 `src/index.ts` — re-exports component

**Tests**
- 🔲 `src/lib/caregiver-roster.component.spec.ts` — loadRoster on init; error state + retry; coverage alert; deactivate guard
- 🔲 `nx test acs-feature-caregiver-roster` PASS
- 🔲 `nx lint acs-feature-caregiver-roster` PASS

**Milestone:**
Wave 3 / P3 Feature libs

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P3

**Acceptance criteria:**
- `npx nx test acs-feature-caregiver-roster` PASS
- `npx nx lint acs-feature-caregiver-roster` PASS
- `npx nx build acs-feature-caregiver-roster` PASS
- loadState='loading' → skeleton (no stale rows)
- loadState='error' → RosterErrorCard + retry (no empty state misidentified)
- `pacientesSemCobertura > 0` → CoverageAlertBar visible (never hidden)
- Cobertura visual: SPEC-02 §1 ASCII tree replicado em DOM

**Dependencies:**
- FE-ACS-010 (AcsCuidadorFacade)
- FE-ACS-012 (CoverageFacade)
- FE-ACS-014 (presentational sub-components)
- FE-ACS-015 (CaregiverRowComponent)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Chama `AcsCuidadorFacade.loadRoster()` que internamente usa `GET /caregivers`

**Testing / Evidence required:**
- Screenshot: roster com rows; sem-cobertura banner visível
- Screenshot: estado de loading (skeleton)
- Screenshot: estado de erro (error card)

**Risks / Notes:**
- A cobertura deve ser derivada do facade (não recalculada no componente). Qualquer prop `coverage` em
  `CuidadorRowVM` já vem calculada do facade — o componente apenas apresenta.
