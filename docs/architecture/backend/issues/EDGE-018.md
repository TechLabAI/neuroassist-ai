# EDGE-018 -- Implement structured JSON logging with MDC (correlationId, tenantId, actor, deviceId) — assert zero biometric/PII/credential in logs

**Status:** DONE -- implementado 2026-08-18 (P7)
**Maturidade:** 3 -- Implementado
**Done/Total:** 4/4 itens

## Contexto
Logging estruturado em JSON com MDC para correlacao de traces. Restricao critica: nenhum dado biometrico, PII, token plaintext, clientSecret ou hash de enrollment pode aparecer em log (LGPD + privacy invariant #6). O campo `deviceId` deve aparecer nos logs de heartbeat mas SEM patient-identifying data alem de `pacienteId` quando necessario.

## Spec de referencia
- SPEC-07

## Escopo
- [x] configurar `logback-spring.xml` com `LogstashEncoder` (JSON por linha com campos `timestamp`, `level`, `logger`, `message`, MDC fields: correlationId, tenantId, actor, deviceId automaticos)
- [x] garantir que `tokenHash`, `secretHash`, `enrollmentToken`, `clientSecret` nunca sao logados — `CredentialMaskingConverter` mascara `cs_*` e `et_*` em PatternLayout; DeviceRegistrationService nunca passa plaintext para log
- [x] teste de privacidade via `CredentialMaskingConverter` — mascaramento de `cs_[hex]` e `et_[hex]` em mensagens de log; validacao adicional via grep dos log statements no codigo-fonte
- [x] `CorrelationIdFilter` popula `correlationId` no MDC (P5); `TenantContextFilter` agora popula `tenantId` e `actor` (sub); `HeartbeatService` popula `deviceId` via MDC try/finally

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
