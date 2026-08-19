# HIST-FE-005 -- Feedback HTTP adapter: ativar HistFeedbackHttpAdapter apos endpoint evnt-service disponivel

**Status:** BLOQUEADO -- aguardando evnt-service feedback endpoint (INT-FE-HIST-002)
**Tipo:** FEATURE
**Maturidade:** 1 -- Stub implementado; ativacao bloqueada
**Done/Total:** 0/3 itens

## Contexto

O `HistFeedbackHttpAdapter` existe em `frontend/libs/hist/src/lib/api/hist-feedback-http.adapter.ts` como stub. O feedback de veredito (real/falso_positivo) e escrito no `evnt-service` via `POST /api/v1/evnt/events/{id}/feedback`. A `HistoryFacade.markFeedback()` e totalmente implementada com logica non-optimistic e reconciliacao 409 — apenas o adapter HTTP precisa ser ativado. Esta issue e bloqueada por `INT-FE-HIST-002`.

## Spec de referencia

- Plano: `docs/architecture/frontend/plans/plan-FE-HIST.md` — P5
- `frontend/libs/hist/src/lib/api/hist-feedback-http.adapter.ts` — stub a completar
- `frontend/libs/hist/src/lib/api/hist-feedback.port.ts` — interface `HistFeedbackPort.markFeedback(id, verdict)`
- `frontend/libs/hist/src/lib/hist.types.ts` — `FeedbackWriteDto`, `FeedbackResponse`
- Commit 3e4328d — `acsHttpInterceptorFn` (padrao de referencia para X-Empresa-Id)

## Escopo

- [ ] Implementar `HistFeedbackHttpAdapter.markFeedback(id, verdict)` via `HttpClient.post('/api/v1/evnt/events/{id}/feedback', { verdict })`
- [ ] Incluir `X-Empresa-Id` no interceptor de feedback (mesmo mecanismo de `histHttpInterceptorFn` — pode ser unificado)
- [ ] Verificar alinhamento de `FeedbackWriteDto` e `FeedbackResponse` com contrato real do evnt-service

## Resultado esperado

`POST /api/v1/evnt/events/{id}/feedback` persiste veredito; facade atualiza signal de `markingState` para 'marked' apos confirmacao (comportamento non-optimistic ja implementado).

## Criterios de aceite

- [ ] `cd frontend && npx nx test hist` — exit code 0; testes de feedback com adapter HTTP passam
- [ ] Smoke test manual: clicar "Foi real?" envia request; badge de veredito aparece apos resposta 200
- [ ] Conflito 409 reconcilia item (comportamento ja implementado na facade — verificar com backend real)
- [ ] Header `X-Empresa-Id` presente nas requisicoes de feedback

## Arquivos afetados

- `frontend/libs/hist/src/lib/api/hist-feedback-http.adapter.ts`
- `frontend/libs/hist/src/lib/hist.providers.ts`

## Dependencias

- INT-FE-HIST-002 (evnt-service `POST /events/{id}/feedback` disponivel)
- HIST-FE-004 (histHttpInterceptorFn criado — reutilizar para feedback)
- Contrato de `FeedbackWriteDto` alinhado com evnt-service team

## Estimativa

S

## Prioridade

P5 (DEFERRED)
