## [FE-EVNT-021] EvntShellIntegrationService — registro do AlarmOutlet global (LIFECYCLE-01)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/integration/src/lib/evnt-shell-integration.service.ts` implementa `EvntShellIntegrationService` com `registerAlarmOutlet()` e `unregisterAlarmOutlet()`, conectando `alarmVm` signal ao `ShellOutletRegistry` em `zLayer: 'interrupt'`.

**Context / Problem statement:**
- SPEC-06 §2 define o serviço e seus dois métodos.
- `registerAlarmOutlet()` é chamado em `EVNT_BOOTSTRAP.init` (FE-EVNT-005) — antes de abrir o canal live.
- `unregisterAlarmOutlet()` é chamado em `EVNT_BOOTSTRAP.destroy` — libera o outlet sem vazamento (LIFECYCLE-01).
- O `ShellOutletRegistry` é de `@neuroassist/shared` (scope:web bridge); o EVNT apenas registra e cancela.

**Approach (execution path):**
1) Criar `libs/evnt/integration/src/lib/evnt-shell-integration.service.ts` (`@Injectable({ providedIn: 'root' })`).
2) Injetar `EventDeliveryFacade` e `ShellOutletRegistry`.
3) `registerAlarmOutlet()`: chamar `shell.registerGlobalOutlet('alarm', { component: () => import(..).then(m => m.AlarmOverlayComponent), visible: facade.alarmVm, zLayer: 'interrupt' })`.
4) `unregisterAlarmOutlet()`: chamar `shell.unregisterGlobalOutlet('alarm')`.
5) Exportar via `libs/evnt/integration/src/index.ts`.

**Non-goals:**
- Implementação do `ShellOutletRegistry` (shared — fora de scope EVNT).
- Renderização do `AlarmOverlayComponent` (delegada ao Shell via registry).

**Layer(s):**
frontend / libs/evnt/integration

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

**Architecture alignment:**
- `providedIn: 'root'` — singleton
- Tag Nx: `scope:evnt`, `type:feature`
- Dependência unidirecional: `scope:evnt` → `scope:web` via interface de registry (não reverse)

**Implementation checklist (by file):**

**Lib integration**
- [ ] `libs/evnt/integration/project.json` — `tags: ["scope:evnt","type:feature"]`
- [ ] `libs/evnt/integration/src/lib/evnt-shell-integration.service.ts`
- [ ] `registerAlarmOutlet()`: `shell.registerGlobalOutlet('alarm', { component, visible: facade.alarmVm, zLayer: 'interrupt' })`
- [ ] `unregisterAlarmOutlet()`: `shell.unregisterGlobalOutlet('alarm')`
- [ ] `visible` ligado a `facade.alarmVm` signal (não re-evaluado a cada render — é o signal em si)
- [ ] `libs/evnt/integration/src/index.ts` re-exporta `EvntShellIntegrationService`

**Tests**
- [ ] `nx test evnt-integration` PASS
- [ ] Unit: `registerAlarmOutlet()` → `shell.registerGlobalOutlet` chamado com key `'alarm'` e `zLayer: 'interrupt'`
- [ ] Unit: `unregisterAlarmOutlet()` → `shell.unregisterGlobalOutlet` chamado com key `'alarm'`
- [ ] `nx lint evnt-integration` PASS

**Milestone:**
Wave P6 / Shell integration

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P6

**Acceptance criteria:**
- `npx nx test evnt-integration` PASS
- `npx nx lint evnt-integration` PASS
- `AlarmOverlayComponent` renderizado pelo Shell acima do router-outlet quando `alarmVm.visible=true`
- Após `unregisterAlarmOutlet()`, nenhum overlay persistente (LIFECYCLE-01)

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade — `alarmVm` signal)
- FE-EVNT-011 (AlarmOverlayComponent — referenciado via dynamic import)
- INT-FE-EVNT-001 (ShellOutletRegistry — interface shared)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Unit: spy em `ShellOutletRegistry.registerGlobalOutlet` — chamado exatamente 1x no `registerAlarmOutlet()`
- e2e: alarme ativo → overlay visível acima do router-outlet + modal (SPEC-06 EARS)

**Risks / Notes:**
- LIFECYCLE-01 gap (M3.5): o `unregisterAlarmOutlet()` deve ser chamado no destroy mesmo se o `registerAlarmOutlet()` falhou — usar try/finally no bootstrap ou verificar se o key existe antes de desregistrar.
