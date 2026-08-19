# INT-FE-HIST-002 -- Integracao FE-BE: ativar evnt-service feedback endpoint para marcacao de veredito

**Status:** BLOQUEADO -- evnt-service feedback endpoint nao disponivel
**Tipo:** INTEGRATION
**Maturidade:** 0 -- Stub implementado; integracao real nao iniciada
**Done/Total:** 0/3 itens

## Contexto

O modulo HIST implementa feedback de veredito (real/falso_positivo) no `HistoryFacade.markFeedback()` com logica non-optimistic e reconciliacao 409 completamente implementados. O target de feedback e o `evnt-service` via `POST /api/v1/evnt/events/{id}/feedback`. O stub `HistFeedbackHttpAdapter` esta pronto mas nao ativado. Esta issue rastreia o desbloqueio e ativacao do feedback HTTP real. Nota: o feedback e escrito em `evnt-service` (nao em `hist-service`) porque auditoria HIST e append-only — o veredito e um evento de correcao no modulo de eventos.

## Spec de referencia

- `docs/architecture/frontend/plans/plan-FE-HIST.md` — P5
- `frontend/libs/hist/src/lib/api/hist-feedback-http.adapter.ts` — stub a completar
- `frontend/libs/hist/src/lib/api/hist-feedback.port.ts` — interface `HistFeedbackPort.markFeedback(id, verdict)`
- `frontend/libs/hist/src/lib/hist.types.ts` — `FeedbackWriteDto`, `FeedbackResponse`
- `docs/architecture/frontend/modules/hist/frontend/frontend-spec/generator-config.yml` — `feedback_backend: evnt-service`
- Commit 3e4328d — `acsHttpInterceptorFn` (padrao de referencia para interceptor multi-empresa)

## Escopo

- [ ] Confirmar que `evnt-service` expoe `POST /api/v1/evnt/events/{id}/feedback` com schema compativel com `FeedbackWriteDto` / `FeedbackResponse`
- [ ] Implementar `HistFeedbackHttpAdapter.markFeedback(id, verdict)` via `HttpClient.post`
- [ ] Garantir `X-Empresa-Id` no interceptor de feedback (pode ser unificado com `histHttpInterceptorFn` de INT-FE-HIST-001)
- [ ] Verificar comportamento de 409 Conflict com endpoint real (facade ja tem logica de reconciliacao)

## Resultado esperado

Marcacao de veredito persiste no `evnt-service` ao vivo. Facade atualiza `markingState` para 'marked' apos confirmacao 200. Conflito 409 reconcilia item sem crash (comportamento non-optimistic ja implementado na facade).

## Criterios de aceite

- [ ] `POST /evnt/events/{id}/feedback` retorna 200 com `FeedbackResponse` (eventId, verdict, confirmedAt)
- [ ] FE: clicar "Foi real?" persiste veredito; badge "Marcado como real" aparece apos resposta
- [ ] Conflito 409: item reconciliado com veredito do backend; sem crash na UI
- [ ] Header `X-Empresa-Id` presente nas requisicoes de feedback

## Condicoes de desbloqueio

- `evnt-service` expoe `POST /api/v1/evnt/events/{id}/feedback`
- Schema de `FeedbackWriteDto` e `FeedbackResponse` alinhado com BE team
- Gateway route para `evnt-service` feedback configurada

## Dependencias

- BE: evnt-service feedback endpoint
- INT-FE-HIST-001 (interceptor multi-empresa criado — reutilizar para feedback)

## Bloqueia

- HIST-FE-005 (HistFeedbackHttpAdapter ativacao)

## Estimativa

S (endpoint simples; logica de facade ja pronta)

## Prioridade

P5 (DEFERRED)
