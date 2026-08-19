# EDGE-008 -- Implement HeartbeatService.recordHeartbeat() — server-stamp ultimaSincronizacao, call DetectionProfilePort, raise domain events, detect liveness crossing

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 5/5 itens

## Contexto
O heartbeat e o evento ancora do EDGE. O servico carimba `ultima_sincronizacao` com o relogio do servidor (nunca do cliente — doc 06 R5), chama `DetectionProfilePort` para obter a versao atual do perfil de deteccao, detecta drift e transicao de liveness (offline -> online), e publica eventos de dominio. Se GEST estiver indisponivel, o heartbeat e aceito no modo degradado (`profilePointerDegraded: true`) — liveness nunca depende de GEST (ADR-006).

## Spec de referencia
- SPEC-02, SPEC-10

## Escopo
- [x] implementar `application/service/HeartbeatService.recordHeartbeat(HeartbeatRequest, deviceId, tenantId)` extraindo `deviceId` do SecurityContext de device token (nunca do body — anti-spoofing)
- [x] carimbar `ultima_sincronizacao = serverNow()` e gravar `versao = agentVersion`, `runningProfileVersion` no agregado
- [x] chamar `DetectionProfilePort.currentPointerForTenant(tenantId)` com fallback degradado (Resilience4j); calcular `profileDrift = (runningProfileVersion != currentProfileVersion)` e publicar `ProfileDriftDetected` se drift
- [x] detectar cruzamento offline -> online (verificar `ultima_sincronizacao` anterior vs. `stalenessThreshold=90s`) e, se ocorrer, append `kind=liveness, fromState=offline, toState=online, actor=system` + publicar `DispositivoVoltouOnline`
- [x] retornar `HeartbeatResponse` com `serverTime`, `currentProfileVersion`, `profileDrift`, `stalenessThresholdSeconds=90`, `nextHeartbeatSeconds=30`; publicar `HeartbeatRecebido` pos-commit

## Resultado esperado
POST /api/v1/edge/heartbeat retorna 200 com `serverTime` carimbado pelo servidor, `profileDrift` correto. GEST indisponivel retorna 200 com `profilePointerDegraded=true`.

## Criterios de aceite
- [ ] `ultima_sincronizacao` gravado com relogio do servidor, nao do request body (nunca aceitar timestamp de cliente)
- [ ] request body com `deviceId` diferente do token JWT retorna 403 `identity-mismatch`
- [ ] device `retired` retorna 409 `device-retired`
- [ ] GEST indisponivel (circuit breaker open): heartbeat aceito com `profilePointerDegraded=true`, liveness nao afetada
- [ ] cruzamento offline->online cria exatamente uma `liveness` transition row com `actor=system`

## Dependencias
- EDGE-007 (precede)

## Estimativa
L

## Prioridade
P2
