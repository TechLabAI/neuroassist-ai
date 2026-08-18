# DASH-014 -- Implement POST /panel/events/{eventId}/confirm: write-through to EVNT (feedback verdict relay)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

O cuidador pode confirmar se um evento detectado foi real ou um falso positivo. O DASH valida
o payload (feedback = `real | falso_positivo`), verifica o escopo do caregiver e encaminha o
veredicto ao EVNT (ADR-006). O DASH retorna o estado atualizado do evento como retornado pelo EVNT.
Nenhum valor e armazenado localmente.

## Spec de referencia

- SPEC-01 (POST /panel/events/{eventId}/confirm, body: {feedback}, 200 echo EVNT, 422 invalid feedback)
- SPEC-02 (write-through only: EVNT e o dono do estado do evento, ADR-006)

## Escopo

- [ ] `api/PanelController.confirmEvent(eventId, @RequestBody ConfirmRequest)`: valida `feedback` e um dos `{real, falso_positivo}` → `422` caso contrario; verifica scope do caregiver; chama `EventStatePort.confirmEvent()`
- [ ] `EventStatePort.confirmEvent(eventId, feedback)`: encaminha ao EVNT `POST /events/{eventId}/feedback { feedback }`, retorna o estado atualizado
- [ ] EVNT rejeita → relay exato do ProblemDetail + status code
- [ ] `eventId` fora do escopo do caregiver → `403`

## Resultado esperado

Feedback do cuidador e persistido pelo EVNT, com estado atualizado retornado ao cliente sem persistencia local.

## Criterios de aceite

- [ ] `feedback = "invalido"` → `422` antes de chamar EVNT
- [ ] `feedback = "real"` com EVNT disponivel → `200` com estado do evento atualizado
- [ ] EVNT retorna `404` (eventId inexistente) → relay `404`
- [ ] Caregiver fora do escopo do evento → `403`

## Dependencias

- DASH-002 (EventStatePort)
- DASH-018 (EvntEventRestAdapter)
- DASH-016 (AcsRestAdapter)

## Estimativa

S

## Prioridade

P4
