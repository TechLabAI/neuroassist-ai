# EDGE-INT-001 -- Integration with GEST — DetectionProfile version pointer (Feign client, circuit breaker, degraded heartbeat fallback)

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 4/4 itens

## Contexto
EDGE chama GEST (`GET /api/v1/gest/detection-profiles/active`) em cada heartbeat para obter a versao atual do perfil de deteccao. Se GEST estiver fora do ar, o heartbeat deve ser aceito no modo degradado (`profilePointerDegraded=true`). Esta issue documenta o contrato de integracao e os testes E2E deste fluxo. Integration-contracts §1.

## Spec de referencia
- SPEC-10, integration-contracts.md

## Escopo
- [x] documentar e validar o contrato EDGE->GEST: endpoint, auth (EDGE service token `edge.internal` ou scope `gest.read`), payload de resposta, timeouts (read=2000ms, connect=500ms) e circuit breaker (50%/10-call, 30s wait)
- [x] criar testes de integracao com WireMock stubbing GEST (response 200, response 404, timeout 3s): verificar os 3 cenarios na heartbeat response
- [x] confirmar com equipe GEST que `GET /api/v1/gest/detection-profiles/active` usa apenas o tenant do service token (nao requer corpo adicional)
- [x] validar que o `X-Correlation-Id` do heartbeat original e propagado para a chamada GEST

## Resultado esperado
Teste E2E: heartbeat com GEST disponivel retorna `currentProfileVersion`; com GEST indisponivel retorna `profilePointerDegraded=true`.

## Criterios de aceite
- [ ] WireMock stub 200: `currentProfileVersion` preenchido na heartbeat response
- [ ] WireMock stub timeout 3s: heartbeat ainda retorna 200 com `profilePointerDegraded=true`
- [ ] WireMock stub 404: `currentProfileVersion=null, profileDrift=false`
- [ ] `X-Correlation-Id` presente na chamada Feign para GEST (verificado no WireMock log)

## Dependencias
- EDGE-014 (precede)

## Estimativa
M

## Prioridade
P4
