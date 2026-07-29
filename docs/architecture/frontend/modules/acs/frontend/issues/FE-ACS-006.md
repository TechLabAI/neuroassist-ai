## [FE-ACS-006] Navigation lib + breadcrumb signal (libs/acs/navigation)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/acs/navigation` provides an `AcsNavService` that exposes a breadcrumb signal consumed by
`AcsShellIntegrationService`. Breadcrumb updates dynamically: roster → `[Cuidadores]`; editor-edit →
`[Cuidadores, Editar "<nome>"]`; linkage → `[Cuidadores, Vínculos de "<paciente>"]`. The signal is silent
while TenantSetup/CaregiverLinkStep run inside the onboarding wizard.

**Context / Problem statement:**
- SPEC-06 §2 and SPEC-01 §1 define the breadcrumb trail per route.
- The breadcrumb for the edit and linkage routes requires dynamic label interpolation (cuidador name, paciente name).
- The nav signal must NOT emit breadcrumbs while onboarding wizard steps are active (shell owns that trail).
- Consumed by FE-ACS-005 (bootstrap subscribes to it) and INT-FE-ACS-002 (integration service contributes
  breadcrumbs to shell).
- Bloqueia: FE-ACS-005 (bootstrap nav subscription), FE-ACS-024 (dynamic breadcrumb enrichment), INT-FE-ACS-002

**Approach (execution path):**
1. Create `libs/acs/navigation/src/lib/acs-nav.service.ts`.
2. Declare `breadcrumbs = signal<BreadcrumbItem[]>([])` and expose as `asReadonly()`.
3. Provide `setBreadcrumb(route: 'roster' | 'editor-new' | 'editor-edit' | 'linkage', params?: {nome?: string; paciente?: string})` method.
4. Provide `clearBreadcrumb()` for onboarding composition context (wizard owns its trail).
5. Provide `AcsMenuConfig` constant exporting the nav menu entry aligned to SPEC-01 §1.
6. Re-export from `libs/acs/navigation/src/index.ts`.
7. Unit tests: signal updates on each `setBreadcrumb` call; clearBreadcrumb emits `[]`.

**Non-goals:**
- No router.events subscription here (smart components drive the signal updates).
- No shell breadcrumb component (shell owns rendering).

**Layer(s):**
frontend / libs/acs/navigation

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-01-route-map.md` (§1 breadcrumb column)

**Architecture alignment:**
- Signals-first: `signal<BreadcrumbItem[]>`; no `BehaviorSubject`
- Tags Nx: `scope:acs`, `type:domain`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/navigation/`
- 🔲 `src/lib/acs-nav.service.ts` — AcsNavService: breadcrumbs signal + setBreadcrumb + clearBreadcrumb
- 🔲 `src/lib/acs-menu.config.ts` — AcsMenuConfig constant (label, icon, route, order)
- 🔲 `src/index.ts` — re-exports AcsNavService, AcsMenuConfig

**Tests**
- 🔲 `src/lib/acs-nav.service.spec.ts` — signal updates for all routes; clearBreadcrumb → []
- 🔲 `nx test acs-navigation` PASS
- 🔲 `nx lint acs-navigation` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P1

**Acceptance criteria:**
- `npx nx test acs-navigation` PASS
- `npx nx lint acs-navigation` PASS
- `npx nx build acs-navigation` PASS
- `setBreadcrumb('editor-edit', {nome: 'Jamilly'})` → signal emits `[{label:'Cuidadores'}, {label:'Editar "Jamilly"'}]`
- `clearBreadcrumb()` → signal emits `[]`
- E2E scenario: "composed wizard steps contribute no ACS breadcrumb" passes

**Dependencies:**
- FE-ACS-003 (lib skeleton must exist)

**Data impact:**
- Nenhum (UI-only signal)

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Testes de signal atualizando corretamente para cada tipo de rota
- E2E: wizard steps sem breadcrumb ACS

**Risks / Notes:**
- `BreadcrumbItem` deve ser o tipo da lib compartilhada (`@neuroassist/shared/contracts`); se não existir ainda,
  definir localmente e migrar depois.
- Interpolação de nome do cuidador/paciente depende de os componentes smart passarem o nome (FE-ACS-024 enriquece).
