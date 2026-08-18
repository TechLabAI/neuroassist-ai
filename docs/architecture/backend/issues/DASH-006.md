# DASH-006 -- Implement InternalNotificationController: POST /internal/notifications dispatch sink with dispatchId idempotency

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/6 itens

## Contexto

O EVNT e o produtor de notificacoes. Quando um evento de urgencia e reconhecido, o EVNT chama
`POST /internal/notifications` no DASH para entrega ao painel do cuidador. O DASH deve:
(a) verificar se o caregiver tem sessao SSE aberta, (b) se sim, pushear o `NotificationEnvelope`
e responder `delivered`, (c) se nao, responder `no_open_session` para que o EVNT aplique seu
mecanismo de retry/fail-open. Idempotencia garantida por `dispatchId` (redelivery nao duplica
o push).

## Spec de referencia

- SPEC-01 (endpoint POST /internal/notifications, auth SVC_EVNT, 202 Accepted)
- SPEC-03 (push SSE + no_open_session)
- SPEC-05 (idempotencia: janela = lifetime da sessao in-memory)

## Escopo

- [ ] `api/InternalNotificationController` com `@PostMapping("/internal/notifications")` (auth: SVC_EVNT authority)
- [ ] `application/NotificationDeliveryService.dispatch(NotificationDispatchRequest)`: verifica sessao aberta por `targetCaregiverId`, pushea `event: notification` com `data: <NotificationEnvelope JSON>`, registra `dispatchId` como "entregue" na sessao
- [ ] Idempotencia: se `dispatchId` ja foi entregue nesta sessao (Set<UUID> por sessao), retornar `202 delivered` sem reemitir o push SSE
- [ ] Se nenhuma sessao aberta para o caregiver: retornar `202 { "delivery": "no_open_session" }` e NAO emitir callback `delivered` para EVNT
- [ ] Teste unitario: `dispatch` com sessao aberta → push SSE + `delivered`; sem sessao → `no_open_session`; redelivery → idempotente

## Resultado esperado

`POST /internal/notifications` aceita e roteia notificacoes para sessoes SSE ativas com deduplicacao por `dispatchId`.

## Criterios de aceite

- [ ] `POST /internal/notifications` sem authority `SVC_EVNT` retorna `401`
- [ ] Com sessao aberta: retorna `202 { "delivery": "delivered" }` e emite `event: notification` na SSE
- [ ] Sem sessao: retorna `202 { "delivery": "no_open_session" }`
- [ ] Redelivery do mesmo `dispatchId` (sessao ainda ativa): retorna `202 delivered` sem segundo push SSE
- [ ] `dispatchId` ausente ou vazio: retorna `400`

## Dependencias

- DASH-004 (PanelSessionRegistry)
- DASH-005 (PanelStreamController: sessao criada)
- DASH-021 (auth SVC_EVNT configurada)

## Estimativa

M

## Prioridade

P2
