## [INT-FE-HIST-005] Integração: evnt-service POST /events/{id}/feedback (feedbackTarget G1)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`FeedbackClient` integrado ao `evnt-service` para o POST de veredicto; `feedbackTarget` flag wired corretamente; gap G1 documentado e resolvido na camada de configuração.

**Context / Problem statement:**
- Backend: `POST /api/v1/evnt/events/{eventId}/feedback { verdict: 'real'|'falso_positivo' }` → 200/201 | 409 | 403 | 5xx.
- Gap G1 (cross-link §3): feedback é propriedade do `evnt-service`; HIST espelha o audit fact via pipeline interno — FE **não** escreve diretamente em hist-service para feedback.
- `feedbackTarget` flag default `'evnt'`; se G1 resolver para proxy hist-service, só a flag muda.
- **Ação necessária:** confirmar endpoint exato do evnt-service e scope do JWT exigido contra o api-contract do EVNT antes de fazer o wiring final.

**Approach (execution path):**
1. Confirmar `evnt-service` endpoint path e scope (`evnt:write`? ou `hist:write`?) via api-contract EVNT.
2. `FeedbackClient.markFeedback` usa base_path resolvido pela flag.
3. Testar com mock: 200, 201, 409 (com body de reconcile), 403, 5xx.
4. Se endpoint ainda não definido no EVNT api-contract → usar `page.route` mock nos e2e e marcar como pending-verification no código.

**Non-goals:**
- Não criar endpoint no evnt-service.

**Layer(s):**
frontend / data-access (integration)

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/contracts/backend-api-cross-link.md` (§3)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-04-api-integration.md` (§2.4)

**Implementation checklist (by file):**
- 🔲 Verificar evnt-service api-contract (caminho e scope)
- 🔲 `FeedbackClient.markFeedback` com flag switch implementado
- 🔲 Unit tests: 200, 201, 409 reconcile, 403, 5xx
- 🔲 `nx test hist-data-access` PASS

**Milestone:** Wave P4 / Hub Integrations

**Labels:**
type:integration · layer:frontend · scope:hist · priority:P4

**Acceptance criteria:**
- `feedbackTarget='evnt'` → POST corretamente para evnt-service.
- 409 response com body `{ reconciledBy, reconciledAt }` → Facade faz reconcile.
- 403 → `mark-error` com mensagem de acesso negado.

**Dependencies:**
- FE-HIST-006, FE-HIST-019

**Open items:**
- G1: endpoint path e scope do evnt-service — verificar antes de wiring final.
