## [TREI-FE-024] Per-card pending spinner + optimistic approval update + rollback on failure

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`ProposalReviewFacade.approveCaregiverSide` implementa o ciclo completo de optimistic update: flip optimista de `caregiverApproved + pending = true` no card, confirmação em sucesso, rollback + refetch + `AppError` retriable em falha — sem re-submissão automática.

**Context / Problem statement:**
- A UX de aprovação deve ser responsiva: o utilizador vê feedback imediato (optimistic flip) sem esperar a latência do BE (SPEC-04 §4).
- Em caso de falha após o flip, o estado deve ser restaurado e o utilizador deve poder re-confirmar (não rollback automático silencioso).
- `pending: boolean` no `ProposedNodeVM` activa um spinner por card — o botão "Aprovar" deve ser desactivado `WHILE pending` para evitar dupla submissão (SPEC-04 §4).
- Esta issue é o polimento do fluxo já arquitectado em TREI-FE-011 — cobre os detalhes de UX e garante que o estado de `pending` é correctamente propagado para o componente.

**Approach (execution path):**
1. Em `ProposalReviewFacade` (TREI-FE-011 — extensão):
   - `approveCaregiverSide({ nodeId, locale })`:
     1. `pending = true` na proposal correspondente.
     2. Flip optimista: `caregiverApproved = true`.
     3. HTTP call `adapter.approveCuidadora(noId, locale)`.
     4. Success: `pending = false`, reconcilia flags da resposta (idempotente).
     5. Error: `pending = false`, rollback `caregiverApproved = false`, chama `inspect(nodeId)` para refetch, emite `AppError` com `retriable: true`.
2. Em `ProposedNodeCardComponent` (TREI-FE-019 — extensão):
   - Botão "Aprovar" `[disabled]="node.pending || node.caregiverApproved || node.clinicalFlag"`.
   - Spinner `@if (node.pending)`.

**Non-goals:**
- Não implementar retry automático (o utilizador confirma manualmente).
- Não tocar na lógica de materialize (sem optimistic para materialize — SPEC-04 §4).

**Layer(s):**
frontend / libs/trei/data-access · libs/trei/feature-proposal-review

**Bounded context:**
trei

**Service execution spec(s):**
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-04-data-access.md` (§4)
- `docs/architecture/frontend/modules/trei/frontend/frontend-spec/SPEC-07-error-handling.md` (§4)

**Architecture alignment:**
- Signals-only (zero BehaviorSubject)
- Tag Nx: `scope:trei`, `type:data-access` / `type:feature`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `proposal-review.facade.ts` — optimistic flip + rollback + refetch completo

**libs/trei/feature-proposal-review**
- [ ] `proposed-node-card.component.ts` — spinner quando `node.pending`, botão disabled durante pending

**Tests**
- [ ] `nx test trei-data-access` PASS
- [ ] `approveCaregiverSide`: `pending = true` antes da resposta, `false` após
- [ ] `approveCaregiverSide` com erro: `caregiverApproved` rolled back, `inspect()` chamado
- [ ] Botão "Aprovar" desactivado durante `pending`
- [ ] `nx lint` PASS

**Milestone:**
Wave 7 / UX Polish

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P7

**Acceptance criteria:**
- `npx nx test trei-data-access` PASS
- `npx nx test trei-feature-proposal-review` PASS
- Teste: `approveCaregiverSide` falha → `caregiverApproved` voltou a `false` + `inspect()` chamado
- Spinner visível durante `pending` (DOM test)

**Dependencies:**
- TREI-FE-011 (ProposalReviewFacade — base da lógica)
- TREI-FE-019 (ProposedNodeCardComponent — adiciona spinner + disable)

**Data impact:**
- Nenhum

**API / Contract impact:**
- `GET /nos/{noId}` chamado no rollback

**Testing / Evidence required:**
- Screenshot: card com spinner activo durante aprovação in-flight
- Screenshot: card restaurado (caregiverApproved = false) após falha com mensagem retriable

**Risks / Notes:**
- O refetch via `inspect()` no rollback pode causar flicker se a resposta chegar lenta — considerar um debounce ou simplesmente aceitar o comportamento.
