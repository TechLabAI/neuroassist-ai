## [FE-EVNT-005] evnt.bootstrap.ts — ciclo de vida init/destroy do módulo

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/shell-entry/src/lib/evnt.bootstrap.ts` implementa `EVNT_BOOTSTRAP` com `init` (registrar outlet → hidratar → abrir canal live → registrar nav) e `destroy` (parar canal → desregistrar outlet), garantindo que nenhum `EventSource` vaze após o módulo ser destruído.

**Context / Problem statement:**
- SPEC-06 §3 define a sequência de init: (1) register AlarmOutlet, (2) hydrate `GET /events/active`, (3) open live channel — a ordem é load-bearing para re-exibir alarme ativo ao recarregar.
- LIFECYCLE-01 gap (M3.5): ao destruir o módulo EVNT, o SSE e o intervalo de polling DEVEM ser cancelados e o AlarmOutlet DEVE ser desregistrado — sem vazamento de EventSource.
- Bloqueia: FE-EVNT-021 (EvntShellIntegrationService) depende do hook `init`; FE-EVNT-007 (EvntClient) deve expor `stopLiveChannel()`.

**Approach (execution path):**
1) Criar `libs/evnt/shell-entry/src/lib/evnt.bootstrap.ts` exportando `EVNT_BOOTSTRAP: ModuleBootstrap`.
2) `init(ctx)`: chamar `ctx.integration.registerAlarmOutlet()`, depois `await ctx.facade.hydrate()`, depois `ctx.facade.startLiveChannel()`, depois `ctx.nav.register(EVNT_NAV_CONFIG)`.
3) `destroy(ctx)`: chamar `ctx.facade.stopLiveChannel()` e `ctx.integration.unregisterAlarmOutlet()`.
4) Garantir que `stopLiveChannel()` cancela tanto o `EventSource` quanto qualquer `setInterval` de polling ativo (LIFECYCLE-01).
5) Re-exportar `EVNT_BOOTSTRAP` via `libs/evnt/shell-entry/src/index.ts`.

**Non-goals:**
- Implementação de `startLiveChannel`/`stopLiveChannel` (FE-EVNT-007/008).
- Implementação de `registerAlarmOutlet` (FE-EVNT-021).

**Layer(s):**
frontend / shell-entry (bootstrap)

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3)

**Architecture alignment:**
- `ModuleBootstrap` shape do `@neuroassist/shared` (a definir se não existe)
- Tag Nx: `scope:evnt`, `type:domain`

**Implementation checklist (by file):**

**Lib shell-entry**
- [ ] `libs/evnt/shell-entry/src/lib/evnt.bootstrap.ts` — `EVNT_BOOTSTRAP` com `init` e `destroy`
- [ ] `init` ordem: registerAlarmOutlet → hydrate → startLiveChannel → register nav (SPEC-06 §3)
- [ ] `destroy` cancela SSE + polling interval + desregistra outlet (LIFECYCLE-01)
- [ ] `libs/evnt/shell-entry/src/index.ts` re-exporta `EVNT_BOOTSTRAP`

**Tests**
- [ ] `nx test evnt-shell-entry` PASS
- [ ] Unit: `destroy()` chamado → `stopLiveChannel()` e `unregisterAlarmOutlet()` invocados
- [ ] Unit: `init()` chamado → `hydrate()` chamado antes de `startLiveChannel()`
- [ ] `nx lint evnt-shell-entry` PASS

**Milestone:**
Wave P1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P1

**Acceptance criteria:**
- `npx nx test evnt-shell-entry` PASS
- `npx nx lint evnt-shell-entry` PASS
- `npx nx build evnt-shell-entry` PASS
- Teste: após `destroy()`, nenhum `EventSource.close()` pendente (spy confirma chamada)

**Dependencies:**
- FE-EVNT-001 (contrato — EVNT_NAV_CONFIG)
- FE-EVNT-007 (EvntClient — `startLiveChannel`/`stopLiveChannel`)
- FE-EVNT-008 (EventDeliveryFacade — `hydrate`)
- FE-EVNT-021 (EvntShellIntegrationService — `registerAlarmOutlet`)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/events/active` chamado no `init` (antes do stream)

**Testing / Evidence required:**
- Unit: spy em `stopLiveChannel` e `unregisterAlarmOutlet` — ambos invocados exatamente uma vez em `destroy()`
- Unit: spy em `hydrate` e `startLiveChannel` — `hydrate` resolvido antes de `startLiveChannel` chamado

**Risks / Notes:**
- LIFECYCLE-01 gap (M3.5): o maior risco é um `EventSource` ou `setInterval` sendo criado em P2 sem um caminho claro de teardown — garantir que `stopLiveChannel()` cubra ambos os casos (SSE ativo e polling ativo).
