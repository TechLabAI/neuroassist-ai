# DASH-010 -- Implement GET /panel/patients/{patientId}/device: EDGE liveness poll with ETag/304 and staleness threshold

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O DASH faz poll condicional ao EDGE a cada 15s (cadencia de heartbeat). A resposta ETag/304
permite reutilizar o ultimo valor conhecido sem trafe extra. Se o EDGE retorna 304, o DASH
serve o valor cacheado. Se o ultimo poll bem-sucedido for mais antigo que `EDGE_STALENESS_SECONDS`
(45s padrao), o DASH reporta `status = "unknown"` e `degraded = true` — nunca deduz `offline`
por staleness (distinzao arquitetural intencional: `offline` e declarado explicitamente pelo EDGE).

## Spec de referencia

- SPEC-01 (GET /panel/patients/{patientId}/device, 200 DeviceStatus)
- SPEC-02 (poll EDGE 15s, ETag/304 honored, staleness → unknown)
- SPEC-07 (EDGE_STALENESS_SECONDS configuravel via env)

## Escopo

- [ ] `application/PanelCompositionService.queryDeviceStatus(caregiverId, patientId)`: valida escopo, chama `DeviceLivenessPort.poll(patientId, lastETag)`, aplica logica de staleness
- [ ] Se EDGE responde `304`: retornar ultimo valor cacheado com `degraded = false`
- [ ] Se `now - lastSuccessfulPollAt > EDGE_STALENESS_SECONDS`: retornar `DeviceStatus{status="unknown", degraded=true}`
- [ ] Se EDGE responde `200` com novo ETag: atualizar cache local e retornar novo status
- [ ] `api/PanelController.getPatientDevice(patientId)`: delega ao service, retorna `DeviceStatus`

## Resultado esperado

`GET /panel/patients/{id}/device` reflete o liveness do dispositivo com degradacao correta quando EDGE e lento ou indisponivel.

## Criterios de aceite

- [ ] EDGE responde `200` com `status=online` → retorna `{status:"online", degraded:false}`
- [ ] EDGE responde `304` → retorna ultimo valor cacheado sem modificacao
- [ ] Poll mais antigo que `EDGE_STALENESS_SECONDS` → retorna `{status:"unknown", degraded:true}`
- [ ] EDGE completamente indisponivel (conexao recusada) → retorna `{status:"unknown", degraded:true}` (nunca "offline")
- [ ] `patientId` fora do escopo → `403`

## Dependencias

- DASH-002 (DeviceLivenessPort)
- DASH-003 (DeviceStatus)
- DASH-016 (AcsRestAdapter)
- DASH-020 (EdgeLivenessRestAdapter)

## Estimativa

M

## Prioridade

P3
