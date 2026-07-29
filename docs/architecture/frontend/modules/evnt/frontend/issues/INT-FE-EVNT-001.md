## [INT-FE-EVNT-001] Integração com ShellOutletRegistry — AlarmOutlet no Shell

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O `EvntShellIntegrationService` integra corretamente com `ShellOutletRegistry` de `@neuroassist/shared` para registrar o `AlarmOverlayComponent` no z-layer `interrupt` acima do router-outlet do Shell.

**Context / Problem statement:**
- O `ShellOutletRegistry` é o ponto de integração FE-Shell — sem ele, o AlarmOutlet nunca é montado.
- Necessário verificar: (a) interface do registry, (b) `zLayer: 'interrupt'` suportado, (c) signal `alarmVm.visible` compatível com o tipo esperado pelo registry.
- Bloqueia o cenário de pré-empção de tela (e2e FE-EVNT-026).

**Approach (execution path):**
1) Verificar a interface de `ShellOutletRegistry` em `@neuroassist/shared`.
2) Confirmar que `registerGlobalOutlet` aceita `{ component: () => Promise<Type>, visible: Signal<AlarmVm>, zLayer: string }`.
3) Se não existir, criar stub de `ShellOutletRegistry` no shared e escalar para o time.
4) Testar integração no `EvntShellIntegrationService` (FE-EVNT-021) com o registry real.

**Non-goals:**
- Implementação do Shell routing.
- Outros outlets além do `alarm`.

**Layer(s):**
frontend / libs/evnt/integration → @neuroassist/shared

**Bounded context:**
evnt (consumidor) + shell (provedor)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-06-shell-integration.md` (§2)

**Architecture alignment:**
- `scope:web` → `scope:evnt` via interface (sem inversão)
- Tag Nx: integração cross-scope (documentar no ADR-005)

**Implementation checklist (by file):**

**Shared / Integration**
- [ ] Verificar `@neuroassist/shared` → `ShellOutletRegistry` interface
- [ ] Confirmar `zLayer: 'interrupt'` suportado
- [ ] `EvntShellIntegrationService.registerAlarmOutlet()` integra com registry real (não mock)
- [ ] Teste de integração: registry real com spy

**Tests**
- [ ] `nx test evnt-integration` PASS (com registry real ou stub)
- [ ] `nx lint evnt-integration` PASS

**Milestone:**
Wave P4 / Hub integrations

**Labels:**
type:integration
layer:frontend
scope:evnt
priority:P4

**Acceptance criteria:**
- `npx nx test evnt-integration` PASS
- AlarmOutlet registrado no Shell acima do router-outlet em runtime

**Dependencies:**
- FE-EVNT-021 (EvntShellIntegrationService)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- e2e: `[role="alertdialog"]` renderizado acima do `<router-outlet>` no DOM tree

**Risks / Notes:**
- Se `ShellOutletRegistry` não existe: criar stub com a mesma interface antes de FE-EVNT-021.
