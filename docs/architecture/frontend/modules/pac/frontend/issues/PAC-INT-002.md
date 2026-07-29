## [PAC-INT-002] PacShellIntegrationService + GEST live-test composition seam

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/integration/src/lib/pac-shell-integration.service.ts` implementa `register()`, `contributeBreadcrumbs()` e `liveTestComposition()`, sendo que `liveTestComposition()` declara a seam de composição para o GEST `GestureLiveTestComponent` ser montado pelo shell page sem que nenhuma lib PAC importe `scope:gest`.

**Context / Problem statement:**
- SPEC-06 §2: `PacShellIntegrationService` bridges PAC no shell sem PAC atingir `scope:web`.
- `liveTestComposition()`: a shell page (não PAC) importa `GestureLiveTestComponent` (GEST-owned) e o renderiza lado a lado com `CommandEditorComponent`, passando o draft via `@Input`/signal (SPEC-06 §2 "shell wires the current Comando draft into GEST via @Input/signal").
- A constraint `scope:pac` NEVER imports `scope:gest` é crítica — verificada via Nx boundary rules.
- `register()` contribui `PAC_MODULE_DESCRIPTOR` + nav config ao shell registry.
- `contributeBreadcrumbs()` expõe o breadcrumb signal de `libs/pac/navigation` ao shell.

**Approach (execution path):**
1. Criar `libs/pac/integration/src/lib/pac-shell-integration.service.ts` com `@Injectable({ providedIn: 'root' })`.
2. `register(shellRegistry: ShellRegistry)`: `shellRegistry.registerModule(PAC_MODULE_DESCRIPTOR)`.
3. `contributeBreadcrumbs(breadcrumbRegistry: BreadcrumbRegistry)`: `breadcrumbRegistry.contribute(MODULE_KEY, pacBreadcrumbService.breadcrumb)`.
4. `liveTestComposition()`: retorna um `CompositionContract<CommandEditorInput>` descrevendo o slot e o tipo do Input esperado — o shell page usa este contrato para instanciar o GEST component. Nenhuma referência direta ao tipo GEST aqui.
5. Criar `apps/pac/src/app/shell/command-editor-shell.page.ts` (thin shell page) que: (a) renders `CommandEditorComponent` via `<pac-command-editor>`; (b) imports GEST `GestureLiveTestComponent` e projeta no slot `[gest-live-test]`; (c) wires `currentDraft = editor.draft()` para GEST `@Input`.
6. Unit test: mock `ShellRegistry.registerModule`, assert chamado com `PAC_MODULE_DESCRIPTOR`. Lint: zero imports `scope:gest` em `libs/pac/`.

**Non-goals:**
- A implementação do `GestureLiveTestComponent` (GEST-owned) não é parte desta issue.
- O routing para `command-editor-shell.page.ts` via shell está em PAC-FE-002.

**Layer(s):**
frontend / libs/pac/integration + apps/pac

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2/§3)

**Architecture alignment:**
- Tag Nx: `scope:pac`, `type:domain`; `apps/pac` tag `scope:pac`, `type:app`
- Nx boundary: nenhum lib `scope:pac` importa `scope:gest`

**Implementation checklist (by file):**

**Lib: libs/pac/integration**
- [ ] `libs/pac/integration/src/lib/pac-shell-integration.service.ts`
- [ ] `libs/pac/integration/src/lib/pac-shell-integration.service.spec.ts`

**App: apps/pac**
- [ ] `apps/pac/src/app/shell/command-editor-shell.page.ts` (imports GEST aqui — OK, app-level)

**Tests**
- [ ] `npx nx test pac-integration` PASS
- [ ] `npx nx lint pac` PASS (zero `scope:gest` em qualquer lib PAC)

**Milestone:**
Wave 4 / P4 Hub Integrations

**Labels:**
type:integration
layer:frontend
scope:pac
priority:P4

**Acceptance criteria:**
- `npx nx lint pac` PASS: zero imports de `scope:gest` em `libs/pac/**`.
- `npx nx test pac-integration` PASS: `register()` chama `ShellRegistry.registerModule(PAC_MODULE_DESCRIPTOR)`.
- e2e PASS: cenário "Composing the GEST live-test at the shell page does not import gest scope into any PAC library".

**Dependencies:**
- PAC-FE-004 (PAC_MODULE_DESCRIPTOR)
- PAC-FE-006 (PacBreadcrumbService)
- PAC-FE-015 (CommandEditorComponent — para o shell page)

**Data impact:**
Nenhum.

**API / Contract impact:**
- O shell page passa `currentDraft: ComandoFormVM` para GEST como @Input; o GEST usa apenas como leitura.

**Testing / Evidence required:**
- Lint report: zero `scope:gest` imports em `libs/pac/`.
- e2e: GEST live-test slot renderiza sem erros.

**Risks / Notes:**
- O `command-editor-shell.page.ts` é o único lugar no projeto PAC onde `scope:gest` é importado — e está em `apps/pac` (app-level, fora de `libs/pac`), portanto não viola a boundary rule de libs.
