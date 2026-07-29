## [FE-EVNT-015] EventListComponent — lista de histórico de eventos recentes

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/feature-routine/src/lib/event-list/event-list.component.ts` renderiza a lista de `recentEvents` (histórico) com navegação para detalhe via `output: open(eventoId)`.

**Context / Problem statement:**
- SPEC-02 §2: `EventListComponent` é apresentacional, recebe `input: events: EventoVm[]` e emite `output: open(eventoId)`.
- É exibido abaixo do `routineQueue` na `EventFeedPageComponent` — representa os eventos já processados/recentes.
- MVP: lista simples sem paginação server-side (paginação é UX polish).

**Approach (execution path):**
1) Criar `libs/evnt/feature-routine/src/lib/event-list/event-list.component.ts` (standalone, OnPush, apresentacional).
2) `input: events: readonly EventoVm[]`.
3) `output: open = new OutputEmitter<string>()` — emite `eventoId` ao clicar em um item.
4) Template: `@for (evento of events(); track evento.eventoId)` → card de evento com `UrgencyBadgeComponent`, `EventTimestampComponent`, `commandLabel`, `feedback` badge.
5) Se `events.length === 0`: estado vazio inline ("Sem eventos recentes.").

**Non-goals:**
- Paginação (UX polish — sprint posterior).
- Filtragem (fora do MVP).

**Layer(s):**
frontend / libs/evnt/feature-routine

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)

**Architecture alignment:**
- Apresentacional: zero DI
- Standalone + OnPush + Signals
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-routine**
- [ ] `libs/evnt/feature-routine/src/lib/event-list/event-list.component.ts`
- [ ] `input: events: readonly EventoVm[]`
- [ ] `output: open: OutputEmitter<string>`
- [ ] `@for` com `track evento.eventoId`
- [ ] Card: `UrgencyBadgeComponent`, `EventTimestampComponent`, `commandLabel`, `feedback`
- [ ] Estado vazio inline quando `events.length === 0`

**Tests**
- [ ] `nx test evnt-feature-routine` PASS
- [ ] Unit: lista com 3 eventos → 3 cards no DOM
- [ ] Unit: clique em card → `(open)` emite `eventoId` correto
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
- Screenshot: lista com 3 eventos, cada um com badge de urgência e timestamp

**Dependencies:**
- FE-EVNT-013 (EventFeedPageComponent — host)
- FE-EVNT-018 (UrgencyBadgeComponent + EventTimestampComponent)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/events` (dados carregados pelo Facade, passados como input)

**Testing / Evidence required:**
- Screenshot: lista com eventos de urgências mistas (emergência + rotina)

**Risks / Notes:**
- Ordenação dos eventos na lista: seguir a ordenação retornada pelo BE (emergência primeiro no payload `ActiveEventsDto`).
