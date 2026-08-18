# DASH-015 -- Implement POST /panel/notifications/{dispatchId}/acknowledge: write-through to EVNT callback (idempotent)

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto

O cuidador reconhece (ack) uma notificacao no painel. O DASH encaminha o acknowledgment ao EVNT
via callback `acknowledged(dispatchId)`. A operacao e idempotente: re-ack do mesmo `dispatchId`
e respondido com `204` sem segunda chamada ao EVNT. O DASH nao armazena estado de ack localmente
(estado duravel vive no EVNT).

## Spec de referencia

- SPEC-01 (POST /panel/notifications/{dispatchId}/acknowledge, 204, idempotente, 403 fora do scope)
- SPEC-03 (acknowledged(dispatchId) e callback para EVNT)
- SPEC-05 (idempotencia: dispatchId)

## Escopo

- [ ] `api/PanelController.acknowledgeNotification(dispatchId)`: valida que `dispatchId` pertence ao scope do caregiver (via EVNT ou escopo local da sessao); chama `NotificationDispatchBackPort.acknowledged(dispatchId)` ao EVNT
- [ ] Idempotencia: EVNT e tolerante a ack duplicado (retorna 2xx); DASH repassa sem retry adicional
- [ ] `dispatchId` de outro caregiver → `403`
- [ ] Retorna `204 No Content` em sucesso

## Resultado esperado

Acknowledge persistido no EVNT; DASH retorna `204` sem armazenar o ack localmente.

## Criterios de aceite

- [ ] `POST .../acknowledge` com `dispatchId` valido → EVNT recebe callback e retorna `204`
- [ ] Re-ack do mesmo `dispatchId` → `204` (idempotente; EVNT nao duplica)
- [ ] `dispatchId` fora do scope → `403`
- [ ] EVNT indisponivel → `503 upstream-unavailable` (nao silenciar falha de ack)

## Dependencias

- DASH-002 (NotificationDispatchBackPort)
- DASH-018 (EvntDispatchCallbackAdapter)
- DASH-016 (AcsRestAdapter: scope)

## Estimativa

S

## Prioridade

P4
