## [FE-DASH-010] HomeStatusPageComponent — container smart (feature-home)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/feature-home` contém `HomeStatusPageComponent` (smart, OnPush) que injeta `DashHomeFacade`, lê os signals `summary()`, `state()` e `calm()` e compõe `StatusCardComponent`, `PatientHeaderComponent` e `QuickLinkListComponent`.

**Context / Problem statement:**
- É o landing padrão do painel (`/`), carregando summary fresco a cada entrada.
- `calm` deriva de EVNT `activeEvent` — não do aggregate. Um "está bem" falso é o pior defeito.
- `state='error'` degrada apenas o StatusCard; shell chrome permanece intacto.
- Fonte: SPEC-02 (feature-home); SPEC-03 §1.
- Bloqueia: FE-DASH-011..012, FE-DASH-022, FE-DASH-023

**Approach (execution path):**
1. Criar lib Nx `dash/feature-home` (type:feature, scope:dash).
2. `HomeStatusPageComponent` injeta `DashHomeFacade` via `inject()`.
3. No `ngOnInit`: chama `facade.reloadSummary()`.
4. Template: projeta `StatusCardComponent` com `vm: HomeStatusCardVm` derivado de `summary()` + `state()` + `calm()`.
5. Projeta `PatientHeaderComponent` com `patient: PatientHeaderVm` (do summary) + `device: DeviceBadgeVm` (input injetado do shell via INT-FE-DASH-002).
6. Projeta `QuickLinkListComponent` com links para HIST.
7. `retry` output de StatusCard → `facade.reloadSummary()`.
8. `viewHistory` output → `facade.viewHistory(patientId)`.

**Non-goals:**
- Não implementar os componentes presentacionais (FE-DASH-011).
- Não importar scope:evnt ou scope:hist.

**Layer(s):**
frontend / libs/dash/feature-home (type:feature)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (feature-home)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§1)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:feature`

**Implementation checklist (by file):**

**Lib: libs/dash/feature-home**
- 🔲 `libs/dash/feature-home/src/lib/home-status-page/home-status-page.component.ts`
- 🔲 `libs/dash/feature-home/src/lib/home-status-page/home-status-page.component.html`
- 🔲 `libs/dash/feature-home/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-feature-home` PASS (testar: reloadSummary chamado no init; state='error' não destrói shell; retry chama reloadSummary)
- 🔲 `npx nx lint dash-feature-home` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:feature
layer:frontend
scope:dash
priority:P3

**Acceptance criteria:**
- `npx nx test dash-feature-home` PASS
- `npx nx lint dash-feature-home` PASS
- `npx nx build dash-feature-home` PASS
- `reloadSummary()` é chamado no init do componente
- `state='error'` renderiza StatusCard no kind 'error' mas não destrói o layout
- Não importa `scope:evnt`, `scope:edge` ou `scope:hist`

**Dependencies:**
- FE-DASH-006 (DashHomeFacade)
- FE-DASH-011 (StatusCardComponent, PatientHeaderComponent, QuickLinkListComponent)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Consome summary via DashHomeFacade (GET /panel/summary)

**Testing / Evidence required:**
- Test com DashHomeFacade mockado via TestBed
- Screenshot do estado calm (green), loading e error

**Risks / Notes:**
- `calm` deve ser verificado em test com `activeEvent` mockado como emergência não confirmada → StatusCard não deve mostrar "está bem".
