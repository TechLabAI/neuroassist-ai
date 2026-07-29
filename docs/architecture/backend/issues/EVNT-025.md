# EVNT-025 -- Hardening: Resilience4j circuit breakers per Feign client, retry budget exhaustion observability, LGPD biometric guard audit log

**Status:** INTACT -- gerado 2026-07-28
**Maturidade:** 0 -- Inicial
**Done/Total:** 0/2 itens

## Contexto
Completes hardening of `evnt-service` for production readiness: (a) final Resilience4j configuration
with per-client circuit breaker thresholds, bulkhead limits, and retry policies; (b) dispatch retry
budget exhaustion surfaced as a Micrometer alert-worthy metric; (c) LGPD biometric-payload rejection
audit-logged as a security event. These are last because they refine behaviors implemented in
EVNT-015..EVNT-019 and EVNT-021.

## Spec de referencia
- SPEC-10 (integrations — circuit breakers)
- SPEC-06 (privacy invariant)
- SPEC-07 (observability)

## Escopo
- [ ] Finalize Resilience4j `application.yml` for each Feign client: `gest`/`pac`/`acs` → `failureRateThreshold=50`, `waitDurationInOpenState=10s`, `permittedCallsInHalfOpenState=3`; `dash`/`hist` → higher tolerance (fail-open path)
- [ ] Add Resilience4j bulkhead (thread pool or semaphore) on the DASH dispatch call to prevent DASH slowness from consuming all threads
- [ ] Emit Micrometer event `evnt.dispatch.exhausted` (counter) when a dispatch reaches `attempts >= max` — alert threshold configurable externally
- [ ] Add security audit log for biometric-payload rejection: log `SECURITY_EVENT:BIOMETRIC_PAYLOAD_REJECTED` with `correlationId`, `deviceId`, `tenant` (no biometric data in the log — only metadata)
- [ ] Load test (manual or Gatling): 100 concurrent ingress reports over 30s → p99 < 500ms; no circuit breaker false-opens
- [ ] ArchUnit rule: assert no domain package imports any `infrastructure` package (hexagonal boundary check)

## Resultado esperado
`evnt-service` is hardened for production: circuit breakers protect upstream dependencies, dispatch
exhaustion is observable and alertable, and biometric rejections are audit-logged.

## Criterios de aceite
- [ ] Resilience4j circuit breaker opens for `gest-service` when failure rate exceeds threshold — subsequent calls fail fast without hitting GEST
- [ ] `evnt.dispatch.exhausted` counter is incremented when `attempts >= EVNT_DISPATCH_RETRY_MAX_ATTEMPTS`
- [ ] Biometric-payload rejection produces a structured log line with `event=BIOMETRIC_PAYLOAD_REJECTED`, `correlationId`, `tenant` — no image data in any log
- [ ] ArchUnit test: `domain` package has no imports from `infrastructure` — exits 0 in `./gradlew test`

## Dependencias
- EVNT-015..EVNT-017 (Feign adapters — circuit breakers wrap these)
- EVNT-009 (dispatch retry sweep — exhaustion detection)
- EVNT-021 (Micrometer — additional counters registered here)
- EVNT-002 (biometric guard — audit log added here)

## Estimativa
M

## Prioridade
P9
