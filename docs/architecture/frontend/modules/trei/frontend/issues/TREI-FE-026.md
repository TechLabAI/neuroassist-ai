## [TREI-FE-026] Telemetry events via @neuroassist/shared/observability (session-started, turn-submitted, node-approved)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`TrainingSessionFacade` e `ProposalReviewFacade` emitem eventos de telemetria para `@neuroassist/shared/observability` nos momentos chave: início de sessão, submissão de turno, e aprovação de nó pela cuidadora.

**Context / Problem statement:**
- O módulo TREI é de alto valor de negócio (autoria assistida por IA) — os eventos de telemetria permitem medir a taxa de sucesso das sessões e a distribuição de aprovações.
- `session-started`: dispara em `startSession()` com sucesso — inclui `{ sessaoId, llmModelId, locale }`.
- `turn-submitted`: dispara em `submitTurn()` — inclui `{ sessaoId, turnIndex }` (sem conteúdo da instrução — dados pessoais).
- `node-approved`: dispara em `approveCaregiverSide()` com sucesso — inclui `{ nodeId, locale, caregiverApproved: true }`.
- Nenhum evento inclui o texto das falas (conteúdo é PHI — SEC-002); apenas IDs de sessão/nó e métricas de comportamento.
- Implementação via `ObservabilityService.track(eventName, payload)` de `@neuroassist/shared/observability`.

**Approach (execution path):**
1. Em `TrainingSessionFacade` (TREI-FE-010 — extensão):
   - Após `startSession()` com sucesso: `observability.track('trei.session.started', { sessaoId, llmModelId, locale })`.
   - Após `submitTurn()` com sucesso: `observability.track('trei.turn.submitted', { sessaoId, turnIndex: turns.length })`.
2. Em `ProposalReviewFacade` (TREI-FE-011 — extensão):
   - Após `approveCaregiverSide()` com sucesso: `observability.track('trei.node.caregiver-approved', { nodeId, locale })`.
3. Injectar `ObservabilityService` em ambas as facades.

**Non-goals:**
- Não emitir conteúdo de falas/texto dos turnos (SEC-002 PHI).
- Não implementar o `ObservabilityService` (pertence a `@neuroassist/shared/observability`).

**Layer(s):**
frontend / libs/trei/data-access

**Bounded context:**
trei

**Service execution spec(s):**
- Sem SPEC directa — alinhamento com padrão de observabilidade dos outros módulos (EVNT/GEST)

**Architecture alignment:**
- Fire-and-forget (não bloqueia o fluxo principal em falha de telemetria)
- Tag Nx: `scope:trei`, `type:data-access`

**Implementation checklist (by file)**

**libs/trei/data-access**
- [ ] `training-session.facade.ts` — `track('trei.session.started', ...)` + `track('trei.turn.submitted', ...)`
- [ ] `proposal-review.facade.ts` — `track('trei.node.caregiver-approved', ...)`
- [ ] Nenhum payload contém texto de falas/instrucao (PHI)

**Tests**
- [ ] `nx test trei-data-access` PASS
- [ ] Spy em `ObservabilityService.track`: `'trei.session.started'` chamado após startSession
- [ ] Payload de `'trei.turn.submitted'` não contém campo `instrucao`
- [ ] `nx lint trei-data-access` PASS

**Milestone:**
Wave 8 / Observability

**Labels:**
type:feature
layer:frontend
scope:trei
priority:P8

**Acceptance criteria:**
- `npx nx test trei-data-access` PASS
- `npx nx lint trei-data-access` PASS
- Nenhum evento de telemetria contém texto das falas ou da instrução (SEC-002)
- `track()` é fire-and-forget (erro não propaga)

**Dependencies:**
- TREI-FE-010 (TrainingSessionFacade)
- TREI-FE-011 (ProposalReviewFacade)
- INT-FE-TREI-006 (ObservabilityService de @neuroassist/shared/observability)

**Data impact:**
- Nenhum (eventos de telemetria são uni-directionais)

**API / Contract impact:**
- Nenhum HTTP directo (ObservabilityService wrapa o envio)

**Testing / Evidence required:**
- Spy: `ObservabilityService.track` chamado com event name correcto
- Revisão de código: payloads sem campos PHI

**Risks / Notes:**
- Confirmar o API do `ObservabilityService` (`track(name, payload)` vs `emit(event)`) antes de implementar — verificar `@neuroassist/shared/observability` nos outros módulos para seguir o padrão.
