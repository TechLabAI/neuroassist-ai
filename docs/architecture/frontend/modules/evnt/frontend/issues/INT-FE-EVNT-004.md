## [INT-FE-EVNT-004] Integração com AccessDecisionService — permissões EVNT

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`evntGuard` e os componentes EVNT integram corretamente com `AccessDecisionService` de `@neuroassist/shared/access` para verificar `evnt.view`, `evnt.confirm` e `evnt.routine.view` da sessão atual.

**Context / Problem statement:**
- SPEC-05 §2 define 3 permissões com semântica específica.
- `evntGuard` (FE-EVNT-020) usa `AccessDecisionService.can('evnt.view')`.
- `EventDetailPageComponent`: `canConfirm` computed = `facade.selectedEvent().feedback === 'nao_avaliado' && AccessDecisionService.can('evnt.confirm')`.
- `EventFeedPageComponent`: `routineNoticesEnabled` = `AccessDecisionService.can('evnt.routine.view')` (ou feature flag).
- `AlarmOverlayComponent` NÃO é gated por permissão — alarme de emergência para qualquer cuidadora autenticada.

**Approach (execution path):**
1) Verificar `AccessDecisionService` em `@neuroassist/shared/access` — interface `can(permission: string): boolean`.
2) Em `evntGuard`: `inject(AccessDecisionService).can('evnt.view')`.
3) No Facade ou `EventDetailPageComponent`: `canConfirm = computed(() => event?.feedback === 'nao_avaliado' && access.can('evnt.confirm'))`.
4) `routineNoticesEnabled = computed(() => access.can('evnt.routine.view') && featureFlags.routineNotices)` (combinação de permissão + flag).
5) `AlarmOverlayComponent`: sem verificação de permissão para renderização; apenas `evnt.confirm` gata o botão de confirmação.

**Non-goals:**
- Implementação de `AccessDecisionService` (shared).
- Guards de autenticação (FE-EVNT-019).

**Layer(s):**
frontend / libs/evnt/access + libs/evnt/data-access → @neuroassist/shared/access

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-05-tool-contract.md` (§2)

**Architecture alignment:**
- `AccessDecisionService` de `@neuroassist/shared/access` (scope:shared)
- Tag Nx: `scope:evnt`, `type:domain`

**Implementation checklist (by file):**

**Libs access + data-access**
- [ ] `evntGuard` injeta e usa `AccessDecisionService.can('evnt.view')`
- [ ] `EventDetailPageComponent` / Facade: `canConfirm` usa `can('evnt.confirm')`
- [ ] `routineNoticesEnabled`: combinação `can('evnt.routine.view') && flag.routineNotices`
- [ ] `AlarmOverlayComponent`: renderização sem permissão; botão gated por `canConfirm`
- [ ] Verificar que `AccessDecisionService` lê da sessão atual (não re-fetcha)

**Tests**
- [ ] `nx test evnt-access` PASS
- [ ] Unit: mock `can('evnt.view')=false` → guard bloqueia
- [ ] Unit: mock `can('evnt.confirm')=false` → `canConfirm=false` no detalhe
- [ ] `nx lint evnt-access` PASS

**Milestone:**
Wave P4 / Hub integrations

**Labels:**
type:integration
layer:frontend
scope:evnt
priority:P4

**Acceptance criteria:**
- `npx nx test evnt-access` PASS
- Sem `evnt.confirm`: botão de confirmação ausente/desabilitado no detalhe
- Alarme de emergência renderizado mesmo sem `evnt.confirm`

**Dependencies:**
- FE-EVNT-020 (evntGuard)
- FE-EVNT-008 (EventDeliveryFacade — `canConfirm` computed)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum diretamente (permissões da sessão, não do BE)

**Testing / Evidence required:**
- Unit: sessão com `evnt.routine.view=false` → `routineNoticesEnabled=false`

**Risks / Notes:**
- A combinação de permissão + feature flag para `routineNoticesEnabled` precisa de precedência clara: flag `off` suprime mesmo com permissão; permissão `off` suprime mesmo com flag `on`.
