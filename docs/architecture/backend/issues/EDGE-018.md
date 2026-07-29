# EDGE-018 -- Implement structured JSON logging with MDC (correlationId, tenantId, actor, deviceId) — assert zero biometric/PII/credential in logs

**Status:** INTACT -- gerado 2026-07-29
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/4 itens

## Contexto
Logging estruturado em JSON com MDC para correlacao de traces. Restricao critica: nenhum dado biometrico, PII, token plaintext, clientSecret ou hash de enrollment pode aparecer em log (LGPD + privacy invariant #6). O campo `deviceId` deve aparecer nos logs de heartbeat mas SEM patient-identifying data alem de `pacienteId` quando necessario.

## Spec de referencia
- SPEC-07

## Escopo
- [ ] configurar `logback-spring.xml` com `JsonLayout` ou `LogstashEncoder` (JSON por linha com campos `timestamp`, `level`, `logger`, `message`, MDC fields: correlationId, tenantId, actor, deviceId)
- [ ] garantir que `tokenHash`, `secretHash`, `enrollmentToken`, `clientSecret` nunca sao logados (adicionar mascaramento no filter ou via pattern de exclusao na configuracao de log)
- [ ] adicionar teste de privacidade: verificar que log de `DeviceRegistrationService.enroll` nao contem nenhum campo com valor de `secret` ou `enrollmentToken`
- [ ] verificar que `CorrelationIdFilter` popula MDC antes do primeiro log do request

## Resultado esperado
Logs em JSON com campos MDC preenchidos; auditoria de privacy: zero credenciais em logs.

## Criterios de aceite
- [ ] Formato JSON valido por linha de log (campos: timestamp, level, logger, message, correlationId, tenantId)
- [ ] Campos `correlationId` e `tenantId` presentes em todo log dentro de um request
- [ ] Teste de privacidade: grep em logs de enroll nao encontra `clientSecret`, `tokenHash`, `enrollmentToken`, ou `secretHash`
- [ ] `deviceId` presente nos logs de heartbeat (extraido do device token claim, nao do body)

## Dependencias
- EDGE-013 (precede)

## Estimativa
S

## Prioridade
P7
