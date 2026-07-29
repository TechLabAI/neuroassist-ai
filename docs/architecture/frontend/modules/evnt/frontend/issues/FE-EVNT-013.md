## [FE-EVNT-013] EventFeedPageComponent — feed de rotina (/eventos)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/feature-routine/src/lib/event-feed-page/event-feed-page.component.ts` renderiza a rota `/eventos` com `routineQueue` (notificações discretas dismissíveis), `recentEvents` (histórico) e banner `DegradedModeBanner` quando `deliveryMode='degraded'`.

**Context / Problem statement:**
- SPEC-02 §2 define `EventFeedPageComponent` (smart): lê `routineQueue`, `recentEvents`, `deliveryMode` do Facade.
- NAV-01 gap (M3.5): navegação para `/eventos` → renderizar feed de rotina hidratado de `GET /events/active`.
- `deliveryMode='degraded'` → banner visível (`DegradedModeBannerComponent` do `@neuroassist/shared/ui-feedback`).
- `routineQueue` vazio e não-loading → `RoutineFeedVm.empty=true` → estado vazio renderizado.
- `dismiss` de rotina: remoção otimista do cliente (sem server call).
- `open` navega para `/eventos/:eventoId`.

**Approach (execution path):**
1) Criar `libs/evnt/feature-routine/src/lib/event-feed-page/event-feed-page.component.ts` (standalone, OnPush).
2) Injetar `EventDeliveryFacade` e `Router`.
3) Leitura de signals: `routineQueue`, `recentEvents`, `deliveryMode`, `loading`, `error`, `empty` (via `RoutineFeedVm` computed).
4) Template: `@if (!empty)` → `RoutineNoticeComponent` (para cada item da `routineQueue`) + `EventListComponent` (histórico). `@if (empty && !loading)` → estado vazio.
5) `DegradedModeBannerComponent` visível `@if (deliveryMode() === 'degraded')`.
6) `(dismiss)` → `facade.dismissRoutine(id)` (remoção otimista).
7) `(open)` → `router.navigate(['/eventos', id])`.

**Non-goals:**
- AlarmOverlayComponent (feature-alarm — diferente lib).
- Histórico paginado completo (MVP: lista simples; paginação é UX polish).

**Layer(s):**
frontend / libs/evnt/feature-routine

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` (§3)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Smart component: injeção de Facade + Router
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-routine**
- [ ] `libs/evnt/feature-routine/src/lib/event-feed-page/event-feed-page.component.ts`
- [ ] Leitura de `routineQueue`, `recentEvents`, `deliveryMode` via Facade
- [ ] `DegradedModeBannerComponent` visível quando `deliveryMode==='degraded'` (NAV-01 + banner)
- [ ] Estado vazio: `@if (empty && !loading)` renderiza mensagem "Nenhuma notificação" (ou similar)
- [ ] Loading state: skeleton ou spinner enquanto `loading=true`
- [ ] `(dismiss)` → `facade.dismissRoutine(id)` (remoção otimista da `routineQueue`)
- [ ] `(open)` → `router.navigate(['/eventos', id])`
- [ ] `libs/evnt/feature-routine/src/index.ts` re-exporta `EventFeedPageComponent`

**Tests**
- [ ] `nx test evnt-feature-routine` PASS
- [ ] Unit: `deliveryMode='degraded'` → `DegradedModeBannerComponent` no DOM
- [ ] Unit: `routineQueue=[]` + `loading=false` → estado vazio renderizado
- [ ] Unit: `(dismiss)` → `facade.dismissRoutine` chamado com id correto
- [ ] `nx lint evnt-feature-routine` PASS

**Milestone:**
Wave P3 / Feature libs

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P3

**Acceptance criteria:**
- `npx nx test evnt-feature-routine` PASS
- `npx nx lint evnt-feature-routine` PASS
- `npx nx build evnt-feature-routine` PASS
- Cobertura visual: feed com notificações, estado vazio e banner degradado (3 screenshots)

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade — signals)
- FE-EVNT-014 (RoutineNoticeComponent)
- FE-EVNT-015 (EventListComponent)
- INT-FE-EVNT-002 (evnt-service GET /events/active + GET /events)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/events` (histórico) e `GET /api/v1/events/active` (hidratação inicial — via Facade)

**Testing / Evidence required:**
- Screenshot: `/eventos` com 2 notificações de rotina + banner degradado visível

**Risks / Notes:**
- NAV-01 gap (M3.5): a hidratação acontece no bootstrap (FE-EVNT-005), não neste componente — o componente apenas lê o signal já populado.
