## [FE-EVNT-008] EventDeliveryFacade — signals, computed selectors e concurrency control

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/data-access/src/lib/event-delivery.facade.ts` implementa `EventDeliveryFacade` com todos os signals de SPEC-03 §5 (`activeEvent`, `routineQueue`, `recentEvents`, `deliveryMode`, `alarmVm`, `selectedEvent(id)`), métodos de mutação (`hydrate`, `startLiveChannel`, `stopLiveChannel`, `confirm`, `loadDetail`, `loadHistory`) e controle de concorrência em confirmação.

**Context / Problem statement:**
- SPEC-03 §5 lista os selectors readonly que componentes consomem — nenhum `BehaviorSubject` exposto.
- CONCUR-01 gap (M3.5): se já existe uma confirmação em voo, o controle de confirmar DEVE estar desabilitado — `alarmVm.confirming=true` sinaliza isso, mas o facade precisa garantir que um segundo `confirm()` simultâneo seja ignorado/bloqueado.
- `confirm()` NÃO é otimista: `activeEvent` é limpo apenas após o POST retornar 200 (SPEC-04 §5).
- Bloqueia: FE-EVNT-011 (AlarmOverlayComponent), FE-EVNT-013 (EventFeedPageComponent), FE-EVNT-016 (EventDetailPageComponent).

**Approach (execution path):**
1) Criar `EventDeliveryFacade` com `WritableSignal`s privados: `_activeEvent`, `_routineQueue`, `_recentEvents`, `_deliveryMode`, `_confirming`, `_confirmError`.
2) Expor signals readonly: `activeEvent = _activeEvent.asReadonly()`, idem para os outros.
3) Implementar `alarmVm = computed(() => ({ event: ..., visible: ..., confirming: ..., confirmError: ..., alreadyConfirmed: ... }))`.
4) Implementar `selectedEvent(id: string): Signal<EventoVm | null>` como `computed`.
5) `hydrate()`: chama `EvntClient.listActive()`, popula `_activeEvent` e `_routineQueue`.
6) `startLiveChannel()`: subscri SSE via `EvntClient.openStream()`, mutações por tipo de evento (`evento.created`, `evento.updated`, `keepalive`).
7) `stopLiveChannel()`: cancela subscription SSE e qualquer intervalo de polling (LIFECYCLE-01).
8) `confirm(id, verdict)`: verificar `_confirming()` — se `true`, retornar imediatamente (CONCUR-01). Caso contrário, setar `_confirming(true)`, fazer POST, em sucesso limpar `activeEvent`, em erro manter `activeEvent` e setar `_confirmError`.
9) Tratar `409 feedback-terminal` como sucesso-equivalente (limpa overlay, mostra "Já confirmado").

**Non-goals:**
- Chamadas HTTP diretas (via `EvntClient`).
- Lógica de feature flags para polling (FE-EVNT-010).
- Mapeamento de erros (INT-FE-EVNT-006).

**Layer(s):**
frontend / libs/evnt/data-access

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` (§2-§5)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-04-data-access.md` (§3-§5)

**Architecture alignment:**
- Signals-only (Angular 21) — sem `BehaviorSubject`, sem NgRx
- `providedIn: 'root'` para ser singleton entre Shell e libs/evnt
- Tag Nx: `scope:evnt`, `type:data-access`

**Implementation checklist (by file):**

**Lib data-access**
- [ ] `libs/evnt/data-access/src/lib/event-delivery.facade.ts` — `EventDeliveryFacade`
- [ ] Signals privados: `_activeEvent`, `_routineQueue`, `_recentEvents`, `_deliveryMode`, `_confirming`, `_confirmError`
- [ ] Signals públicos (readonly): `activeEvent`, `routineQueue`, `recentEvents`, `deliveryMode`
- [ ] `alarmVm = computed(...)` com todos os campos de `AlarmVm` (SPEC-03 §2)
- [ ] `selectedEvent(id): Signal<EventoVm | null>` como `computed`
- [ ] `confirm()`: guard CONCUR-01 — `if (_confirming()) return;` antes de disparar POST
- [ ] `confirm()`: `activeEvent` limpo SOMENTE após 200 (não otimista)
- [ ] `confirm()`: `409 feedback-terminal` → `alreadyConfirmed=true` + limpar overlay
- [ ] `stopLiveChannel()`: cancela SSE subscription + polling interval (LIFECYCLE-01)
- [ ] `libs/evnt/data-access/src/index.ts` re-exporta `EventDeliveryFacade`

**Tests**
- [ ] `nx test evnt-data-access` PASS
- [ ] Unit: `confirm()` chamado duas vezes concorrentes → segundo ignorado (CONCUR-01)
- [ ] Unit: `confirm()` falha de rede → `activeEvent` permanece não-null
- [ ] Unit: `confirm()` 409 → overlay limpo + `alreadyConfirmed=true`
- [ ] `nx lint evnt-data-access` PASS

**Milestone:**
Wave P2 / Data access

**Labels:**
type:feature
layer:frontend
scope:evnt
priority:P2

**Acceptance criteria:**
- `npx nx test evnt-data-access` PASS
- `npx nx lint evnt-data-access` PASS
- `npx nx build evnt-data-access` PASS
- Teste: duas chamadas `confirm()` simultâneas → apenas uma requisição HTTP disparada

**Dependencies:**
- FE-EVNT-007 (EvntClient)
- FE-EVNT-009 (DTO types + mapper)

**Data impact:**
- Nenhum (facade é efêmero — estado em memória Angular)

**API / Contract impact:**
- `POST /api/v1/events/{id}/confirmation` — safety invariant: não-otimista

**Testing / Evidence required:**
- Unit: spy em `EvntClient.confirm` — chamado exatamente 1x mesmo com duplo clique simulado

**Risks / Notes:**
- CONCUR-01 gap (M3.5): o guard mais simples é um `if (_confirming()) return` — sem semáforo elaborado; suffices para MVP.
- 409 como sucesso-equivalente é contra-intuitivo — documentar o motivo no código (outra pessoa já confirmou = objetivo de segurança alcançado).
