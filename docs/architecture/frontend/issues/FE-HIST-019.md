## [FE-HIST-019] hist ui: per-item mark-error state e reconcile de 409

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`FeedbackActionComponent` e `HistoryFacade` lidam corretamente com `mark-error` (falha no POST de feedback) e reconcile de 409 (veredicto já terminal no backend), mantendo a garantia non-optimistic.

**Context / Problem statement:**
- SPEC-06 §1: feedback write failure → `mark-error` no item; "Não foi possível registrar. Tentar de novo."; item permanece `nao_avaliado`.
- SPEC-06 §1: `409 already-terminal` → reconcile ao veredicto do backend; "Já marcado por {nome} às {hora}"; sem override client-side.
- FE-GAP-09 (Specs-Validate): cenário de 403 no feedback write (caregiver perde acesso mid-session).
- FE-GAP-12: `correlationId` visível no error de item.

**Approach (execution path):**
1. Em `HistoryFacade.markFeedback`: no `catchError` → se `409` → extrair `reconciledBy`/`reconciledAt` da resposta → `ItemMarking{state:'marked', reconciledBy, reconciledAtLabel}`; outros erros → `state:'mark-error'`.
2. `FeedbackActionComponent`: estado `mark-error` → "Não foi possível registrar. Tentar de novo." com retry.
3. Estado reconciled (409): "Já marcado por {nome} às {hora}" — terminal, sem retry.
4. `403` no feedback write → `mark-error` + mensagem de acesso negado.
5. Unit tests em `HistoryFacade`: 5xx, 409, 403, network.

**Non-goals:**
- Não implementar lógica de retry global — apenas per-item manual.

**Layer(s):**
frontend / data-access + ui

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-06-error-states.md` (§1, §4)

**Architecture alignment:**
- Signals-first · Standalone + OnPush

**Implementation checklist (by file):**
- 🔲 Atualizar `history.facade.ts` — catchError com 409 reconcile + mark-error
- 🔲 Atualizar `history.facade.spec.ts` — cenários 409, 5xx, 403, network
- 🔲 Atualizar `feedback-action.component.ts` — estado mark-error + 409 reconcile UI
- 🔲 Atualizar `feedback-action.component.spec.ts`
- 🔲 `nx test hist-data-access` PASS
- 🔲 `nx test hist-ui` PASS

**Milestone:** Wave P5 / Error States

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P5

**Acceptance criteria:**
- Falha 5xx → `state=mark-error`; item permanece `nao_avaliado`.
- `409` → `state=marked`; `reconciledBy`/`reconciledAtLabel` exibidos; sem retry.
- `403` feedback write → `mark-error` com mensagem adequada.
- `correlationId` presente na mensagem de erro per-item.

**Dependencies:**
- FE-HIST-013, FE-HIST-017
