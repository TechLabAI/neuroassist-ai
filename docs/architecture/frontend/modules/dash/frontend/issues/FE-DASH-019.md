## [FE-DASH-019] dash.bootstrap.ts — init (SSE + summary prime) e destroy (SSE close + region release)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/shell-entry/src/lib/dash.bootstrap.ts` implementa o ciclo de vida de inicialização (registra descriptor + nav, declara outlet region, abre SSE session quando sessão existe, prima summary no primeiro Home entry) e destruição (fecha SSE, libera outlet region, limpa signals). Cobre coverage gaps G6 e G7.

**Context / Problem statement:**
- O bootstrap é chamado pelo shell quando o DashModule é carregado.
- Destroy deve fechar o EventSource e liberar a região do outlet (coverage gap G7).
- Fonte: SPEC-06 §3; coverage gaps G6 (bootstrap register nav+outlet) e G7 (destroy SSE+region).
- Bloqueia: FE-DASH-028 (e2e G6), FE-DASH-029 (e2e G7)

**Approach (execution path):**
1. Criar `DashBootstrap` (classe ou função) em `libs/dash/shell-entry/src/lib/dash.bootstrap.ts`.
2. `init()`:
   - Chamar `DashShellIntegrationService.registerWithShell(shell)` (idempotente).
   - Declarar `'dash.global-interrupt'` no `ShellOutletRegistry`.
   - Se `authSession.isAuthenticated()` → chamar `DashStreamClient.open()` e `NotificationsFacade.loadUnacknowledged()`.
   - Registrar listener para quando sessão muda para autenticada (para abrir SSE em login tardio).
3. `destroy()`:
   - Chamar `DashStreamClient.close()`.
   - Chamar `DashShellIntegrationService.releaseOutletRegion()`.
   - Limpar signals de `DashHomeFacade` e `NotificationsFacade`.
4. Chamar `destroy()` no `ngOnDestroy` do `DashModule` ou via `DestroyRef`.

**Non-goals:**
- Não registrar o descriptor no shell (feito em FE-DASH-004).
- Não implementar SSE (FE-DASH-008).

**Layer(s):**
frontend / libs/dash/shell-entry (type:domain)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3)

**Architecture alignment:**
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/shell-entry**
- 🔲 `libs/dash/shell-entry/src/lib/dash.bootstrap.ts`
- 🔲 `libs/dash/shell-entry/src/lib/dash.module.ts` (chamar bootstrap init/destroy via DestroyRef)

**Tests**
- 🔲 `npx nx test dash-shell-entry` PASS (testar: init abre SSE quando autenticado; destroy fecha SSE; destroy libera outlet region)
- 🔲 `npx nx lint dash-shell-entry` PASS

**Milestone:**
Wave 6 / Shell Integration

**Labels:**
type:domain
layer:frontend
scope:dash
priority:P6

**Acceptance criteria:**
- `npx nx test dash-shell-entry` PASS
- `npx nx lint dash-shell-entry` PASS
- `npx nx build dash-shell-entry` PASS
- `init()` com sessão autenticada → `DashStreamClient.open()` chamado (coverage gap G6)
- `destroy()` → `DashStreamClient.close()` + `releaseOutletRegion()` chamados (coverage gap G7)
- Destroy sem SSE aberto não lança exceção

**Dependencies:**
- FE-DASH-008 (DashStreamClient)
- FE-DASH-018 (DashShellIntegrationService)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Abre `/panel/stream` SSE via DashStreamClient

**Testing / Evidence required:**
- Test verifica sequência init → destroy com mocks de DashStreamClient e DashShellIntegrationService
- e2e: FE-DASH-028 (G6) e FE-DASH-029 (G7)

**Risks / Notes:**
- Se o módulo for destruído antes da sessão autenticar, SSE não deve tentar abrir. Verificar condição no destroy.
