# DASH-018 -- Implement EvntEventRestAdapter + EvntDispatchCallbackAdapter (EventStatePort + NotificationDispatchBackPort)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O EVNT e consumido pelo DASH em duas direcoes: (1) leitura de estado de eventos e notificacoes
nao-acknowledgeadas, e write-through de confirmacao de feedback; (2) callbacks de entrega
`delivered(dispatchId)` e `acknowledged(dispatchId)`. Por serem responsabilidades distintas,
implementadas em dois adapters separados: `EvntEventRestAdapter` (EventStatePort) e
`EvntDispatchCallbackAdapter` (NotificationDispatchBackPort).

## Spec de referencia

- SPEC-10 (`EventStatePort`, `NotificationDispatchBackPort`, upstream: `evnt-service`)
- SPEC-05 (idempotencia: callbacks por dispatchId sao idempotentes no EVNT)

## Escopo

- [ ] `EvntEventRestAdapter implements EventStatePort`:
  - `fetchLatestEvents(patientIds)`: `GET /patients/{id}/events` por paciente (paralelo ou batch)
  - `fetchUnacknowledged(caregiverId)`: `GET /caregivers/{id}/notifications?state=unacknowledged`
  - `confirmEvent(eventId, feedback)`: `POST /events/{eventId}/feedback { feedback }` → retorna estado atualizado
- [ ] `EvntDispatchCallbackAdapter implements NotificationDispatchBackPort`:
  - `reportDelivered(dispatchId)`: `POST /dispatches/{dispatchId}/delivered` (idempotente)
  - `reportAcknowledged(dispatchId)`: `POST /dispatches/{dispatchId}/acknowledged` (idempotente)
- [ ] EVNT down em reads → `UpstreamUnavailableException` (soft-degrade no caller)
- [ ] EVNT down em callbacks → log e retry (max 1); se falhar, log de aviso (fail-open: EVNT tem seu proprio retry)
- [ ] WireMock tests: fetch ok, confirmEvent relay, callback delivered, EVNT down

## Resultado esperado

Estado de eventos e callbacks de entrega gerenciados via EVNT com resiliencia fail-open para callbacks.

## Criterios de aceite

- [ ] `fetchUnacknowledged` retorna lista de `NotificationEnvelope` do EVNT
- [ ] `confirmEvent` repassa feedback e retorna estado atualizado do EVNT
- [ ] `reportDelivered` chama EVNT e e idempotente (reenvio nao duplica registro)
- [ ] `reportAcknowledged` idem
- [ ] EVNT down em callback → log warn, nao lanca excecao para o caller principal (fail-open)

## Dependencias

- DASH-002 (EventStatePort, NotificationDispatchBackPort interfaces)

## Estimativa

M

## Prioridade

P5
