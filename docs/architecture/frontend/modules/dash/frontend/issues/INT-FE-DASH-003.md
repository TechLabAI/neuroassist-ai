## [INT-FE-DASH-003] ShellOutletRegistry: declarar região dash.global-interrupt para EVNT

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`DashShellIntegrationService.registerWithShell()` declara a região `'dash.global-interrupt'` no `ShellOutletRegistry` de `@neuroassist/shared/ui-components`, tornando o host disponível para que EVNT registre seu `AlarmOverlayComponent`.

**Context / Problem statement:**
- DASH **declara** o outlet host; EVNT **registra** o conteúdo (AlarmOverlayComponent/toast) — separação clara de responsabilidades.
- Se a região não for declarada, EVNT não pode montar o alarme (falha silenciosa — risco crítico).
- Fonte: SPEC-05 §4; SPEC-06 §1/§2; ADR-005.
- Cobre coverage gap G6 (bootstrap register nav+outlet — lado do outlet).

**Approach (execution path):**
1. Em `DashShellIntegrationService.registerWithShell()` (FE-DASH-018): chamar `ShellOutletRegistry.declareRegion('dash.global-interrupt')`.
2. Em `releaseOutletRegion()`: chamar `ShellOutletRegistry.releaseRegion('dash.global-interrupt')`.
3. No template do shell host (scope:web), adicionar `<ng-container [shellOutletRegion]="'dash.global-interrupt'">` no nível z:interrupt.
4. Verificar que o host container tem `z-index` adequado para sobrepor o conteúdo normal.
5. Verificar que EVNT pode registrar seu componente via `ShellOutletRegistry.registerContent('dash.global-interrupt', AlarmOverlayComponent)` sem importar scope:dash.

**Non-goals:**
- Não implementar `AlarmOverlayComponent` (EVNT).
- Não implementar `ShellOutletRegistry` (shared/ui-components).

**Layer(s):**
frontend / libs/dash/integration (type:domain) + apps/neuroassist-ai-web (scope:web)

**Bounded context:**
dash ↔ evnt (outlet declarado por dash, conteúdo por evnt)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-05-tool-contract.md` (§4)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1/§2)

**Architecture alignment:**
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/integration**
- 🔲 `libs/dash/integration/src/lib/dash-shell-integration.service.ts` (declareRegion + releaseRegion — já em FE-DASH-018)

**App: apps/neuroassist-ai-web**
- 🔲 `apps/neuroassist-ai-web/src/app/shell/app-shell.component.html` (adicionar shellOutletRegion container)

**Tests**
- 🔲 `npx nx test dash-integration` PASS (ShellOutletRegistry.declareRegion chamado com 'dash.global-interrupt')
- 🔲 `npx nx test neuroassist-ai-web` PASS (container com data-outlet-region presente no DOM)
- 🔲 `npx nx lint dash-integration` PASS

**Milestone:**
Wave 4 / Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:dash
priority:P4

**Acceptance criteria:**
- `npx nx test dash-integration` PASS
- `npx nx lint dash-integration` PASS
- `data-outlet-region="dash.global-interrupt"` presente no DOM após bootstrap
- `releaseRegion` chamado no destroy

**Dependencies:**
- FE-DASH-018 (DashShellIntegrationService)
- `@neuroassist/shared/ui-components` (ShellOutletRegistry — deve existir)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- e2e FE-DASH-028 verifica a presença do outlet no DOM

**Risks / Notes:**
- Crítico para EVNT: se a região não for declarada antes de EVNT tentar registrar conteúdo, o alarme não monta. Validar a ordem de bootstrap.
