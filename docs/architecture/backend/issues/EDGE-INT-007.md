# EDGE-INT-007 -- Integration with DASH — provide liveness poll endpoints (ETag/304, serverTime in response body) for caregiver connectivity signal

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
DASH depende do endpoint de liveness para mostrar ao cuidador se o monitoramento esta ativo. O campo `serverTime` na `LivenessView` e hard dependency do FE para derivar a idade relativa ao servidor (evitando drift de relogio do cliente — warning aberto M3, fechado aqui). ETag/304 para poll eficiente. Integration-contracts §3.

## Spec de referencia
- SPEC-02, integration-contracts.md

## Escopo
- [ ] garantir que `LivenessView` contem o campo `serverTime` (ISO-8601 UTC, relogio do servidor no momento da resposta) em TODA resposta de GET /devices/{id}/liveness e GET /patients/{id}/liveness
- [ ] garantir que ETag e calculado sobre o bucket de status (nao apenas `ultima_sincronizacao`): cruzamento online->offline invalida cache mesmo sem novo heartbeat
- [ ] verificar que `X-Staleness-Threshold-Seconds: 90` e incluido no response header de liveness e heartbeat
- [ ] criar teste de integracao: poll com `If-None-Match=<etag>` e status inalterado retorna 304; apos staleness retorna 200 com `status=offline` e novo ETag

## Resultado esperado
DASH consegue derivar estado de conectividade com `serverTime`; poll com ETag retorna 304 quando status nao mudou; `X-Staleness-Threshold-Seconds` presente.

## Criterios de aceite
- [ ] `serverTime` presente em toda resposta de GET /devices/{id}/liveness (hard dependency FE — M3 warning fechado)
- [ ] ETag invalida quando `now - ultima_sincronizacao > 90s` mesmo sem novo heartbeat
- [ ] 304 retornado quando ETag coincide e status nao mudou
- [ ] `X-Staleness-Threshold-Seconds: 90` presente no response header

## Dependencias
- EDGE-009 (precede)

## Estimativa
S

## Prioridade
P4
