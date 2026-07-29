## [FE-ACS-014] Roster presentational sub-components (RosterKpiBar, CoverageAlertBar, RosterEmptyState, RosterErrorCard)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Four pure presentational components live in `libs/acs/feature-caregiver-roster` (not in `acs/ui` — they are
feature-specific). All are standalone, OnPush, receive `@Input` only, emit `@Output` events. No facade access.
`CoverageAlertBarComponent` renders a danger banner listing pacientes sem cobertura — it is ALWAYS visible when
the input array is non-empty (ADR-005).

**Context / Problem statement:**
- SPEC-02 §1 defines the sub-component tree with exact `@Input`/`@Output` signatures.
- `RosterKpiBarComponent` shows `totalAtivos`, `coordenadores`, `pacientesSemCobertura`.
- `CoverageAlertBarComponent` receives `semCobertura: PatientCoverageVM[]` and shows danger — never hidden.
- `RosterEmptyStateComponent` shows when roster is empty (post-onboarding edge case); emits `(invite)`.
- `RosterErrorCardComponent` shows on load error; emits `(retry)`.
- Bloqueia: FE-ACS-013 (smart component renders these)

**Approach (execution path):**
1. Create each component as a standalone, OnPush class in
   `libs/acs/feature-caregiver-roster/src/lib/components/`.
2. `RosterKpiBarComponent`: `@Input({ required: true }) kpis: RosterKpiVM` — render three KPI tiles.
3. `CoverageAlertBarComponent`: `@Input({ required: true }) semCobertura: PatientCoverageVM[]` — danger
   banner with patient names list; hidden only if `semCobertura.length === 0` (never hidden while items exist).
4. `RosterEmptyStateComponent`: no inputs; `@Output() invite = new EventEmitter<void>()`.
5. `RosterErrorCardComponent`: `@Input({ required: true }) error: AppError`; `@Output() retry`.
6. Unit tests: KpiBar renders three tiles; CoverageAlert shows danger for length>0; ErrorCard shows retry button.

**Non-goals:**
- No facade access.
- No routing logic.
- Individual caregiver row is a separate issue (FE-ACS-015 — it lives in `acs/ui` shared lib).

**Layer(s):**
frontend / libs/acs/feature-caregiver-roster

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§1)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-03-view-models.md` (§1 RosterKpiVM)

**Architecture alignment:**
- Standalone, OnPush; @Input/@Output only; no signal access
- Tags Nx: `scope:acs`, `type:feature`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/feature-caregiver-roster/src/lib/components/`
- 🔲 `roster-kpi-bar.component.ts` + `.html` — 3 KPI tiles
- 🔲 `coverage-alert-bar.component.ts` + `.html` — danger banner (never hidden when input non-empty)
- 🔲 `roster-empty-state.component.ts` + `.html` — invite CTA
- 🔲 `roster-error-card.component.ts` + `.html` — error + retry button

**Tests**
- 🔲 `*.component.spec.ts` for each — input binding + output emission
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
- `CoverageAlertBar(semCobertura=[...])` → danger banner visível
- `CoverageAlertBar(semCobertura=[])` → nada renderizado
- `RosterErrorCard` emite `retry` no click

**Dependencies:**
- FE-ACS-003 (lib skeleton)
- FE-ACS-012 (PatientCoverageVM type, via data-access exports)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshots: KPI bar (3 tiles); coverage alert (danger); empty state; error card

**Risks / Notes:**
- `CoverageAlertBarComponent` deve listar os NOMES dos pacientes sem cobertura — isso requer que a
  `PatientCoverageVM` inclua `pacienteNome`. Verificar que `CoverageFacade` inclui o nome.
