## [PAC-FE-005] pac.bootstrap.ts — init/destroy lifecycle

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/shell-entry/src/lib/pac.bootstrap.ts` implementa `ModuleBootstrap` com `init(ctx)` que registra permissions/flags, assina o breadcrumb signal de `libs/pac/navigation` e pré-carrega `PacPacienteFacade.loadCurrent()`; e `destroy()` que cancela subscriptions, resetam os signals das facades e aborta GET em-flight do catálogo.

**Context / Problem statement:**
- O bootstrap é chamado pelo shell quando o usuário navega para o módulo; `init` prepara o estado mínimo para que `pacTenantPatientGuard` resolva rapidamente na primeira navegação (SPEC-06 §3).
- `destroy` deve ser um reset completo: `PacComandoFacade` e `PacPacienteFacade` voltam ao estado inicial; GET em-flight do catálogo é abortado (SPEC-06 §3 "abort any in-flight catalog GET").
- PAC não tem global store — destroy é full reset de seus signal stores locais.
- Bloqueia: nenhuma issue de implementação de feature (mas sem bootstrap, a guards falham por falta de estado inicial)

**Approach (execution path):**
1. Criar `libs/pac/shell-entry/src/lib/pac.bootstrap.ts`.
2. Implementar `init(ctx: ShellContext)`:
   - Registrar `MODULE_PERMISSION_MAP` + `MODULE_FEATURE_FLAGS` via `ctx.permissionRegistry.register(...)`.
   - Assinar `breadcrumbSignal` de `PacBreadcrumbService` e contribuir ao `ctx.breadcrumbRegistry`.
   - Chamar `PacPacienteFacade.loadCurrent()` para priming (non-blocking; guard usa o signal resultante).
3. Implementar `destroy()`:
   - Desassinar todas as subscriptions do `init`.
   - Chamar `PacComandoFacade.reset()` e `PacPacienteFacade.reset()`.
   - Chamar `PacComandoFacade.abortInFlightLoad()` (aborta o GET do catálogo se ativo).
4. Unit test: spy em `ctx.permissionRegistry.register`, assert que após `init` o patient facade tem loadCurrent chamado; após `destroy` os signals são `null/idle`.

**Non-goals:**
- Sem inicialização de câmera ou detecção GEST — PAC bootstrap não importa `scope:gest`.
- Sem providers globais adicionados ao root injector — providers PAC-specific vão em `pac.providers.ts` (INT-FE-PAC-004).

**Layer(s):**
frontend / libs/pac/shell-entry

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3)

**Architecture alignment:**
- `ModuleBootstrap` interface de `@neuroassist/shared/contracts`
- Tag Nx: `scope:pac`, `type:domain`
- Sem import de `scope:gest`

**Implementation checklist (by file):**

**Lib: libs/pac/shell-entry**
- [ ] `libs/pac/shell-entry/src/lib/pac.bootstrap.ts`
- [ ] `libs/pac/shell-entry/src/lib/pac.bootstrap.spec.ts`

**Tests**
- [ ] `npx nx test pac-shell-entry` PASS
- [ ] `npx nx lint pac-shell-entry` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P1

**Acceptance criteria:**
- `npx nx test pac-shell-entry` PASS: `init` registra permissions e chama `loadCurrent`.
- `npx nx test pac-shell-entry` PASS: `destroy` reseta `PacPacienteFacade.patient()` para `null`.
- `npx nx test pac-shell-entry` PASS: `destroy` aborta GET em-flight (AbortController mock).
- Nenhum import de `scope:gest` no arquivo (lint boundary).

**Dependencies:**
- PAC-FE-001 (MODULE_PERMISSION_MAP, MODULE_FEATURE_FLAGS)
- PAC-FE-006 (PacBreadcrumbService)
- PAC-FE-010 (PacPacienteFacade.loadCurrent / reset)
- PAC-FE-011 (PacComandoFacade.reset / abortInFlightLoad)

**Data impact:**
Nenhum persistente — apenas estado de signals em memória.

**API / Contract impact:**
- `PacPacienteFacade.loadCurrent()` dispara `GET /api/pac/v1/pacientes?size=1`; deve ser chamada apenas uma vez por sessão de módulo (o guard reutiliza o signal).

**Testing / Evidence required:**
- Unit: spy em facades, assert calls em `init` e `destroy`.

**Risks / Notes:**
- Se `destroy` não abortar o GET em-flight, o observable pode resolver APÓS o re-mount do módulo, sobreescrevendo o estado novo — race condition. Usar `AbortController` ou `takeUntilDestroyed` no facade.
