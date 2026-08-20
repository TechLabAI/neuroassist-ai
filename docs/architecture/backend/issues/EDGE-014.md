# EDGE-014 -- Implement DetectionProfileClient Feign adapter (GET /gest/detection-profiles/active) with Resilience4j circuit breaker and degraded fallback

**Status:** DONE
**Maturidade:** 85 -- Maduro
**Done/Total:** 6/6 itens

## Contexto
No heartbeat, EDGE consulta GEST para obter a versao atual do perfil de deteccao. Esta chamada e tolerante a falhas: se GEST estiver indisponivel, o heartbeat e aceito no modo degradado (`profilePointerDegraded=true`). O Feign client descobre `gest-service` via REG (sem URL hardcoded). Integration-contracts §1.

## Spec de referencia
- SPEC-10

## Escopo
- [x] criar `domain/port/DetectionProfilePort.java` com metodo `Optional<Long> currentPointerForTenant(String tenantId)`
- [x] criar `infrastructure/integration/DetectionProfileClientAdapter.java` implementando `DetectionProfilePort`
- [x] configurar `@FeignClient(name='gest-service', path='/api/v1/gest')` para discovery via REG; endpoint: GET /detection-profiles/active
- [x] configurar timeouts (read=2000ms, connect=500ms) e 1 retry em `ConnectException/ReadTimeoutException`
- [x] configurar Resilience4j circuit breaker: threshold 50% / 10-call window; wait 30s para HALF_OPEN; fallback retorna `Optional.empty()` (degraded)
- [x] propagar `X-Correlation-Id` no header da chamada Feign; usar service token (`edge.internal`) para autenticar na chamada GEST

## Resultado esperado
Chamada ao GEST retorna versao ativa; GEST indisponivel ativa o fallback degradado sem falhar o heartbeat.

## Criterios de aceite
- [ ] GEST disponivel: `currentProfileVersion` preenchido na heartbeat response
- [ ] GEST retorna 404 no-active-profile: `currentProfileVersion=null, profileDrift=false`
- [ ] GEST indisponivel (simulado com WireMock): heartbeat retorna 200 com `profilePointerDegraded=true`
- [ ] Circuit breaker abre apos 50% de falhas em 10 chamadas
- [ ] `X-Correlation-Id` presente em toda chamada Feign para GEST

## Dependencias
- EDGE-008 (precede)

## Estimativa
M

## Prioridade
P4
