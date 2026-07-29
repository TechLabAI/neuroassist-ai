## [FE-EVNT-022] Loading states + empty state do feed de rotina

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O `EventFeedPageComponent` renderiza skeleton/spinner enquanto `loading=true`, estado vazio ("Sem notificações pendentes") quando `empty=true`, e o breadcrumb `[Eventos]` está sempre visível independentemente do estado.

**Context / Problem statement:**
- SPEC-03 §3 define `RoutineFeedVm.loading` e `RoutineFeedVm.empty` como campos obrigatórios.
- O estado de carregamento inicial (após `EVNT_BOOTSTRAP.init` chamar `hydrate()`) deve ser visualmente representado.
- `empty = !loading && routineQueue.length === 0` — estado explicitamente modelado, não inferido do DOM.

**Approach (execution path):**
1) Na `EventFeedPageComponent` (FE-EVNT-013): adicionar `@if (loading())` → renderizar skeleton loader (3 cards placeholder).
2) `@if (empty() && !loading())` → renderizar estado vazio com ícone + "Sem notificações pendentes. Aguardando eventos…".
3) Garantir que o breadcrumb `[Eventos]` é renderizado em todos os estados (loading, empty, populated).
4) Loading state: usar skeleton da `@neuroassist/shared/ui-feedback` (se disponível) ou CSS animado simples.

**Non-goals:**
- Loading state do `EventDetailPageComponent` (parte de FE-EVNT-016).
- Paginação do histórico.

**Layer(s):**
frontend / libs/evnt/feature-routine

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` (§3)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-routine (EventFeedPageComponent)**
- [ ] `@if (loading())` → skeleton/spinner (3 linhas placeholder)
- [ ] `@if (empty() && !loading())` → estado vazio com mensagem pt-BR
- [ ] Breadcrumb `[Eventos]` visível em todos os estados
- [ ] `loading` signal derivado de `facade.routineFeedVm().loading`

**Tests**
- [ ] `nx test evnt-feature-routine` PASS
- [ ] Unit: `loading=true` → skeleton no DOM, sem `RoutineNoticeComponent`
- [ ] Unit: `empty=true, loading=false` → mensagem de estado vazio no DOM
- [ ] `nx lint evnt-feature-routine` PASS

**Milestone:**
Wave P7 / UX polish

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P7

**Acceptance criteria:**
- `npx nx test evnt-feature-routine` PASS
- Screenshot: skeleton durante carregamento
- Screenshot: estado vazio com ícone e mensagem
- Screenshot: feed com 2 notificações (estado populado)

**Dependencies:**
- FE-EVNT-013 (EventFeedPageComponent)
- FE-EVNT-008 (EventDeliveryFacade — `loading` signal)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot de cada um dos 3 estados (loading, empty, populated)

**Risks / Notes:**
- O `loading` signal deve ser setado para `true` no início de `hydrate()` e `false` no fim — garantir que o Facade (FE-EVNT-008) expõe isso corretamente.
