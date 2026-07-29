## [FE-EVNT-016] EventDetailPageComponent — detalhe de evento (/eventos/:eventoId)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/evnt/feature-routine/src/lib/event-detail-page/event-detail-page.component.ts` renderiza a rota `/eventos/:eventoId` com detalhe do evento, `FeedbackVerdictControl` (quando `canConfirm=true`), `ConfirmationTrailComponent` e tratamento de loading/not-found.

**Context / Problem statement:**
- SPEC-02 §2 define `EventDetailPageComponent` (smart): lê `selectedEvent(id)` do Facade.
- NAV-02 gap (M3.5): navegação para `/eventos/:eventoId` → renderizar detalhe do evento específico.
- GUARD-03 gap (M3.5): `eventoId` não pertence ao tenant da cuidadora → redirect sem revelar existência (coberto pelo guard, mas a page trata 404/403 recebidos).
- `canConfirm` é falso quando `feedback !== 'nao_avaliado'` ou sem permissão `evnt.confirm`.
- `CONCUR-01`: `confirming=true` → `FeedbackVerdictControl` desabilitado.

**Approach (execution path):**
1) Criar `libs/evnt/feature-routine/src/lib/event-detail-page/event-detail-page.component.ts` (standalone, OnPush).
2) Injetar `EventDeliveryFacade`, `ActivatedRoute`, `Router`.
3) Ler `eventoId` de `route.snapshot.paramMap.get('eventoId')`.
4) Chamar `facade.loadDetail(eventoId)` no `ngOnInit`.
5) Signal `selectedEvent = facade.selectedEvent(eventoId)`.
6) Template: loading state → detalhe → `FeedbackVerdictControl` (se `canConfirm()`) → `ConfirmationTrailComponent`.
7) Breadcrumb dinâmico: `[Eventos > {commandLabel} ({displayTime})]` (NAV-02).
8) Tratamento 404: redirect para `/eventos` (ERR-01 — coberto em FE-EVNT-023).

**Non-goals:**
- Implementação do `FeedbackVerdictControl` (próxima issue).
- Guards de redirect (FE-EVNT-019/020).

**Layer(s):**
frontend / libs/evnt/feature-routine

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` (§4)

**Architecture alignment:**
- Smart: injeta Facade + Router
- Standalone + OnPush + Signals
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-routine**
- [ ] `libs/evnt/feature-routine/src/lib/event-detail-page/event-detail-page.component.ts`
- [ ] Lê `eventoId` de `ActivatedRoute`
- [ ] `facade.loadDetail(eventoId)` no init
- [ ] `selectedEvent = facade.selectedEvent(eventoId)` via `computed`
- [ ] Template: loading → detalhe → `FeedbackVerdictControl` (se `canConfirm`) → `ConfirmationTrailComponent`
- [ ] `FeedbackVerdictControl` `[disabled]="confirming()"` (CONCUR-01)
- [ ] Breadcrumb dinâmico `[Eventos > {commandLabel} ({displayTime})]` (NAV-02)
- [ ] `UrgencyBadgeComponent` e `EventTimestampComponent` no detalhe

**Tests**
- [ ] `nx test evnt-feature-routine` PASS
- [ ] Unit: `selectedEvent=null` + `loading=false` → estado não-encontrado
- [ ] Unit: `canConfirm=false` → `FeedbackVerdictControl` ausente ou `[disabled]`
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
- Breadcrumb exibe `[Eventos > {rótulo} ({hora})]` (NAV-02)
- `FeedbackVerdictControl` visível apenas quando `feedback === 'nao_avaliado'` + `evnt.confirm`

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade — `selectedEvent`, `confirming`)
- FE-EVNT-017 (FeedbackVerdictControl + ConfirmationTrailComponent)
- FE-EVNT-020 (evntGuard — proteção de rota)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /api/v1/events/{eventoId}` via Facade.loadDetail

**Testing / Evidence required:**
- Screenshot: detalhe com badge, timestamp, label, `FeedbackVerdictControl` habilitado
- Screenshot: detalhe com `feedback='real'` → `FeedbackVerdictControl` ausente

**Risks / Notes:**
- GUARD-03 gap (M3.5): o guard redireciona antes de a page ser montada — a page só vê 404 se o guard deixar passar (evento válido mas deletado). Ambos os casos devem redirecionar para `/eventos`.
