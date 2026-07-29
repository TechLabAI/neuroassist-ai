# SPEC-07 — Observability + health

> Structured access logging, metrics, correlation continuity, health/readiness. Mitigates
> R-6 (LGPD in logs). EARS per standard.

## Objetivo
Especificar como o gateway se torna observável: logs de acesso estruturados, métricas,
continuidade de correlação e endpoints de saúde/prontidão.

## Escopo
- **In:** access log estruturado, MDC `empresaId`/`correlationId`, métricas (counters de
  rota/upstream/rate-limit/auth), health e readiness, política LGPD de logs.
- **Out:** pipeline de coleta/retention (infra), APM/tracing distribuído completo (futuro;
  correlationId é a base).

## Decisões técnicas
- Cada requisição roteada emite uma linha `request.routed`; cada rejeição emite
  `request.rejected` (ver `events/event-catalog.md`).
- **LGPD (R-6):** logs carregam apenas ids/rota/status/latência — **nunca** corpo de
  request/response nem PII além de ids de tenant/principal.
- Métricas expostas em `/actuator/prometheus`.
- `readiness` só fica `UP` quando registrado no Eureka **e** JWK do ACS alcançável (cache
  quente).

## Implementação
- `StructuredAccessLogger` (support/) escreve JSON com campos canônicos.
- MDC populado por `CorrelationIdFilter`/`TenantContextPropagationFilter`.
- Health indicators customizados: `eurekaRegistration`, `acsJwk`.

## Casos de borda
- JWK refresh falhando → readiness pode ficar `DOWN` + alerta `auth.jwk.refresh.failed`.
- Requisição rejeitada pré-auth → log sem `empresaId` (usar `-`).

## Critérios de aceite (EARS)
- WHEN a request is forwarded, THE SYSTEM SHALL emit a structured access line with
  `correlationId`, `empresaId`, `route`, `upstreamStatus` and `latencyMs`.
- THE SYSTEM SHALL NOT log request or response bodies, nor PII beyond tenant/principal ids
  (LGPD, R-6).
- WHILE the gateway is not registered with Eureka OR the ACS JWK set is unavailable with a
  cold cache, THE SYSTEM SHALL report readiness `DOWN`.
- THE SYSTEM SHALL expose gateway metrics (rate-limit, upstream-error, auth-failure
  counters) at `/actuator/prometheus`.
