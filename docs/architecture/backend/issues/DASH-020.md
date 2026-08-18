# DASH-020 -- Implement EdgeLivenessRestAdapter (DeviceLivenessPort): conditional GET + ETag + staleness-to-unknown mapping

**Status:** INTACT -- gerado 2026-08-17
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/5 itens

## Contexto

O DASH faz poll ao EDGE a cada 15s para obter o liveness do dispositivo do paciente. O EDGE
suporta requisicoes condicionais (ETag + If-None-Match): se o status nao mudou, responde
`304 Not Modified`, economizando banda. Se o ultimo poll bem-sucedido e mais antigo que
`EDGE_STALENESS_SECONDS` (45s), o DASH reporta `unknown/degraded` — nunca infere `offline`
por staleness (semantica arquitetural: `offline` e declarado explicitamente pelo EDGE).

## Spec de referencia

- SPEC-10 (`DeviceLivenessPort`, upstream: `edge-service`, `EdgeLivenessRestAdapter`)
- SPEC-07 (`EDGE_STALENESS_SECONDS` configuravel)

## Escopo

- [ ] `infrastructure/adapter/EdgeLivenessRestAdapter implements DeviceLivenessPort` usando `RestTemplate` ou Feign com suporte a ETag
- [ ] Estado local por patientId: `{ lastETag: String, lastValue: DeviceStatus, lastSuccessAt: Instant }`
- [ ] `pollDeviceStatus(patientId)`:
  - Chamada `GET /devices?patientId={id}` com `If-None-Match: <lastETag>` (se disponivel)
  - `200`: atualiza `lastValue`, `lastETag`, `lastSuccessAt`; retorna `lastValue`
  - `304`: retorna `lastValue` sem atualizar `lastSuccessAt` (status nao mudou, poll foi bem-sucedido em termos de conectividade)
  - EDGE down ou timeout: nao atualiza `lastSuccessAt`; se `now - lastSuccessAt > EDGE_STALENESS_SECONDS` → retorna `DeviceStatus{status="unknown", degraded=true}`
- [ ] Scheduler (ou chamada lazy) de poll a cada `HEARTBEAT_SECONDS` por patientId ativo (via PanelSession)
- [ ] WireMock test: 200 com ETag, 304 reutiliza cache, EDGE down → unknown apos staleness

## Resultado esperado

Liveness do dispositivo servida com eficiencia (ETag) e degradacao semanticamente correta (unknown, nao offline).

## Criterios de aceite

- [ ] `200` com novo ETag → `lastValue` e `lastETag` atualizados; retorna DeviceStatus correto
- [ ] `304` → retorna `lastValue` sem reprocessamento
- [ ] EDGE down com `lastSuccessAt` recente (< staleness) → retorna ultimo valor com `degraded=false`
- [ ] EDGE down com `lastSuccessAt` stale → retorna `{status:"unknown", degraded:true}`
- [ ] `status = "offline"` e definido somente pelo EDGE (DASH nunca escreve "offline" por staleness)

## Dependencias

- DASH-002 (DeviceLivenessPort interface)
- DASH-003 (DeviceStatus)

## Estimativa

M

## Prioridade

P5
