# INT-DASH-005 -- DASH -> EDGE liveness: conditional GET with ETag/304 honor + staleness-to-unknown mapping

## Contexto

O EDGE suporta requisicoes condicionais (ETag + If-None-Match). O DASH deve honrar `304 Not Modified`
e servir o valor cacheado localmente. Este issue valida o contrato de integracao DASH→EDGE do
ponto de vista condicional, incluindo a semantica de staleness (`unknown`, nao `offline`).

## Spec de referencia

- SPEC-10 (`DeviceLivenessPort`, upstream: `edge-service`)
- `contracts/integration-contracts.md §5` (EDGE: condicional ETag/304, staleness EDGE_STALENESS_SECONDS)

## Escopo

- WireMock EDGE respondendo `200` com `ETag: "abc"` → DASH armazena ETag + valor
- Segunda chamada: DASH envia `If-None-Match: "abc"` → WireMock responde `304` → DASH retorna valor cacheado sem chamar de novo o EDGE (verificar apenas 1 request real ao WireMock)
- EDGE down com lastSuccessAt recente (< staleness): retorna ultimo valor cacheado
- EDGE down com lastSuccessAt stale (> EDGE_STALENESS_SECONDS): retorna `DeviceStatus{status="unknown", degraded=true}` (nao "offline")
- Verificar: `status = "offline"` nao e escrito pelo DASH por staleness (apenas pelo EDGE explicitamente)

## Resultado esperado

Poll condicional reduz trafego ao EDGE; staleness resulta em `unknown/degraded`, nunca `offline`.

## Criterios de aceite

- [ ] Segunda chamada dentro do TTL envia `If-None-Match` e respeita `304`
- [ ] `304` reutiliza `lastValue` sem request adicional ao EDGE
- [ ] Stale > `EDGE_STALENESS_SECONDS` → `{status:"unknown", degraded:true}`
- [ ] DASH nunca escreve `status = "offline"` por staleness

## Dependencias

- DASH-010 (device endpoint)
- DASH-020 (EdgeLivenessRestAdapter)

## Estimativa

M

## Prioridade

P3
