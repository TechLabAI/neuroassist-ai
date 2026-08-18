# INT-DASH-002 -- DASH -> EVNT delivered/acknowledged callbacks: idempotency by dispatchId, WireMock contract test

## Contexto

Apos entregar uma notificacao ou receber um ack do cuidador, o DASH reporta ao EVNT via
callbacks REST (`POST /dispatches/{dispatchId}/delivered` e `POST /dispatches/{dispatchId}/acknowledged`).
Estes callbacks sao idempotentes por `dispatchId` no EVNT. Este issue valida o contrato de
callback do ponto de vista do DASH (produtor).

## Spec de referencia

- SPEC-05 (idempotencia dos callbacks por dispatchId)
- SPEC-10 (`NotificationDispatchBackPort`, upstream: `evnt-service`)
- `contracts/event-contracts.md §3` (callbacks delivered + acknowledged)

## Escopo

- Teste de contrato WireMock: `EvntDispatchCallbackAdapter.reportDelivered(dispatchId)` → WireMock stub EVNT responde `200`; verificar que o DASH chamou `POST /dispatches/{id}/delivered`
- Teste: `reportAcknowledged(dispatchId)` → idem para `POST /dispatches/{id}/acknowledged`
- Teste de idempotencia: EVNT responde `200` em segunda chamada com mesmo `dispatchId` → DASH nao lanca excecao
- Teste de falha: EVNT down → log de aviso, DASH nao propaga erro para o caregiver request (fail-open para callback)

## Resultado esperado

Callbacks de entrega/ack chegam ao EVNT exatamente uma vez por operacao; falha do EVNT nao quebra o fluxo principal.

## Criterios de aceite

- [ ] `reportDelivered` dispara `POST /dispatches/{dispatchId}/delivered` no EVNT
- [ ] `reportAcknowledged` dispara `POST /dispatches/{dispatchId}/acknowledged` no EVNT
- [ ] EVNT down → log `WARN`, sem excecao propagada para o caller (fail-open)
- [ ] WireMock verify: cada callback chamado exatamente 1x por operacao

## Dependencias

- DASH-018 (EvntDispatchCallbackAdapter)
- INT-DASH-001 (auth service-to-service alinhada)

## Estimativa

M

## Prioridade

P2
