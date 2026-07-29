## [FE-EVNT-017] FeedbackVerdictControl + ConfirmationTrailComponent

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`FeedbackVerdictControl` (apresentacional) expõe `input: feedback`, `input: disabled` e `output: verdict('real'|'falso_positivo')`, permitindo à cuidadora registrar o veredicto do evento. `ConfirmationTrailComponent` exibe quem e quando confirmou.

**Context / Problem statement:**
- SPEC-02 §2 / SPEC-03 §4: `FeedbackVerdictControl` emite `(verdict)` → `EventDetailPageComponent.onVerdict()` → `facade.confirm(id, verdict)`.
- `canConfirm = feedback === 'nao_avaliado' && hasPermission('evnt.confirm')` (SPEC-03 §4).
- `CONCUR-01`: `[disabled]="confirming()"` enquanto POST em voo.
- `ConfirmationTrailComponent` é presentacional: `input: confirmadoPor`, `input: confirmadoAt`.

**Approach (execution path):**
1) Criar `FeedbackVerdictControl` (standalone, OnPush): `input: feedback`, `input: disabled`, `output: verdict`.
2) UI: dois botões/radio — "Real" e "Falso Positivo". Ambos `[disabled]` quando `disabled=true`.
3) Criar `ConfirmationTrailComponent` (standalone, OnPush): `input: confirmadoPor: string | null`, `input: confirmadoAt: string | null`. Renderiza "Confirmado por {nome} às {hora}" se não-null.
4) No `EventDetailPageComponent`: ligar `(verdict)` → `facade.confirm(eventoId, verdict)`.

**Non-goals:**
- Lógica de confirmação no Facade (FE-EVNT-008).
- Validação de permissão aqui — `canConfirm` já é computado pelo Facade/EventDetailPageComponent.

**Layer(s):**
frontend / libs/evnt/feature-routine

**Bounded context:**
evnt

**Service execution spec(s):**
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (§2)
- `docs/architecture/frontend/modules/evnt/frontend/frontend-spec/SPEC-03-view-models.md` (§4)

**Architecture alignment:**
- Apresentacional: sem DI
- Standalone + OnPush
- Tag Nx: `scope:evnt`, `type:feature`

**Implementation checklist (by file):**

**Lib feature-routine**
- [ ] `libs/evnt/feature-routine/src/lib/feedback-verdict-control/feedback-verdict-control.component.ts`
- [ ] `input: feedback: Feedback`, `input: disabled: boolean`
- [ ] `output: verdict: OutputEmitter<'real' | 'falso_positivo'>`
- [ ] Botões "Real" e "Falso Positivo" com `[disabled]="disabled"` (CONCUR-01)
- [ ] `libs/evnt/feature-routine/src/lib/confirmation-trail/confirmation-trail.component.ts`
- [ ] `input: confirmadoPor: string | null`, `input: confirmadoAt: string | null`
- [ ] Renderiza "Confirmado por {nome} às {hora}" se não-null

**Tests**
- [ ] `nx test evnt-feature-routine` PASS
- [ ] Unit: `disabled=true` → ambos os botões `[disabled]`
- [ ] Unit: clique "Real" → `(verdict)` emite `'real'`
- [ ] Unit: `confirmadoPor=null` → `ConfirmationTrailComponent` renderiza vazio
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
- Screenshot: detalhe com dois botões, spinner ao confirmar, trilha de confirmação após sucesso

**Dependencies:**
- FE-EVNT-008 (EventDeliveryFacade — `confirm` + `confirming` signal)
- FE-EVNT-016 (EventDetailPageComponent — host)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `POST /api/v1/events/{id}/confirmation` com `verdict` no body

**Testing / Evidence required:**
- Screenshot: `feedback='real'` → trilha "Confirmado por Jamilly às 14h32" visível

**Risks / Notes:**
- A trilha de confirmação é lida diretamente do `EventoVm` (campos `confirmadoPor`, `confirmadoAt`) — não precisa de nova chamada de API após o POST (o Facade atualiza o evento via SSE ou resposta do POST).
