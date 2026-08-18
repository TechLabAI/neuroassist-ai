## [FE-DASH-026] e2e Playwright: notification delivery (SSE + lista + acknowledge)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/dash-e2e/src/notification-delivery.spec.ts` cobre os cenários do feature file existente: entrega SSE de notificação discreet, append sem re-fetch, acknowledge idempotente, lista vazia e lista em erro (coverage gap G5).

**Context / Problem statement:**
- Feature file existente: `docs/architecture/frontend/modules/dash/frontend/e2e/notification-delivery.feature`.
- Coverage gap G5: lista vazia/erro inline.
- Fonte: SPEC-03 §2; SPEC-04 §4.

**Approach (execution path):**
1. Criar `apps/dash-e2e/src/notification-delivery.spec.ts`.
2. Cenários do feature file original.
3. Cenário G5a: simular `/panel/notifications/unacknowledged` retornando lista vazia → verificar `EmptyOrErrorStateComponent` visível.
4. Cenário G5b: simular erro de rede em `/panel/notifications/unacknowledged` → verificar estado de erro inline (sem bloquear Home).
5. Cenário SSE: usar `page.route()` para simular evento SSE `notification` → verificar append na lista sem re-fetch.
6. Cenário acknowledge: clicar em "marcar como lido" → verificar `POST .../acknowledge`; simular falha → verificar que flag reverte sem toast de erro.

**Non-goals:**
- Não testar o SSE em produção real (mock via Playwright).

**Layer(s):**
frontend / apps/dash-e2e (type:e2e)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/e2e/notification-delivery.feature`
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§2)

**Architecture alignment:**
- Playwright; `scope:dash`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/dash-e2e**
- 🔲 `apps/dash-e2e/src/notification-delivery.spec.ts`
- 🔲 `apps/dash-e2e/src/fixtures/notification-api.fixture.ts` (mocks SSE + REST)

**Tests**
- 🔲 `npx nx e2e dash-e2e --spec notification-delivery` PASS
- 🔲 `npx nx lint dash-e2e` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:dash
priority:P9
coverage-gap:G5

**Acceptance criteria:**
- `npx nx e2e dash-e2e --spec notification-delivery` PASS
- Lista vazia → EmptyOrErrorStateComponent inline visível (G5a)
- Erro de API → estado de erro inline, Home intacto (G5b)
- Acknowledge falho → flag reverte silenciosamente (sem toast)
- SSE evento → append sem re-fetch completo

**Dependencies:**
- FE-DASH-013 (NotificationCenterPageComponent)
- FE-DASH-015 (EmptyOrErrorStateComponent)
- FE-DASH-007 (NotificationsFacade)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Mocks de `/panel/notifications/**` e SSE via Playwright

**Testing / Evidence required:**
- Screenshots: lista vazia, lista com erro, notificação SSE recebida

**Risks / Notes:**
- Simular SSE no Playwright pode exigir interceptação de `EventSource` ou um servidor de mock SSE local; investigar abordagem antes de implementar.
