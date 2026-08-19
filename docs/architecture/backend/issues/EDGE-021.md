# EDGE-021 -- Implement optimistic-lock row_version enforcement (409 on stale write), Device detail endpoint (GET /devices/{id}), and DeviceListService with pagination

**Status:** PARTIAL
**Maturidade:** 85 -- Maduro
**Done/Total:** 7/8 itens

## Contexto
Hardening: protecao contra writes concorrentes via `row_version` (SPEC-05), endpoint completo GET /devices/{id} com status/drift derivados em read-time (SPEC-01 §4.1), e listagem paginada de devices por tenant (SPEC-01 §4 pagination convention). Estes itens fecham o escopo P9 do EDGE.

## Spec de referencia
- SPEC-05, SPEC-01

## Escopo
- [x] verificar que `@Version` em `DispositivoEdge.rowVersion` esta mapeado e que `OptimisticLockException` e convertida para 409 `optimistic-lock` no `GlobalExceptionHandler` com campo `correlationId`
- [x] implementar `DeviceController.getDevice(deviceId)` chamando `LivenessService.evaluate()` e `DetectionProfilePort` para derivar `status`, `profileDrift`, `stalenessThresholdSeconds` em read-time (nao coluna armazenada)
- [x] implementar listagem paginada `GET /api/v1/edge/devices?page=0&size=50` com resposta `{items, page, size, total}` filtrando por `tenantId` do JWT (max size=200)
- [ ] criar teste de integracao: duas requisicoes concorrentes de pair ao mesmo device retornam 409 `optimistic-lock` na segunda

## Resultado esperado
GET /devices/{id} retorna DeviceView com status/drift derivados; escrita concorrente retorna 409; listagem paginada funciona com isolamento por tenant.

## Criterios de aceite
- [ ] Write concorrente com `row_version` stale retorna 409 `optimistic-lock` com `correlationId`
- [ ] GET /devices/{id} deriva `status` corretamente em read-time (nao le coluna `status`)
- [ ] GET /devices?page=0&size=50 retorna `{items, page, size, total}` filtrado por tenant
- [ ] Device de outro tenant nao aparece na listagem (isolamento por tenantId do JWT)

## Dependencias
- EDGE-009 (precede)
- EDGE-010 (precede)

## Estimativa
M

## Prioridade
P9
