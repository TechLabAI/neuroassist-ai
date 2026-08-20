# SPEC-07 — Observability & Health

> Service: `hist-service` · Module: HIST · Refs: ADR-005, ADR-006 · EARS notation (D-057).

## Objetivo

Definir logging estruturado, métricas, health checks e propagação de correlação.

## Escopo

**In:** logs JSON, métricas Micrometer, actuator health/readiness, `X-Correlation-Id` propagation. **Out:**
tracing distribuído completo (fora do MVP; só propagação de correlação).

## Decisões técnicas

- **Logging**: JSON estruturado; cada linha carrega `correlationId`, `tenant`, `factType`, `streamKey`
  quando aplicável; nunca loga `payload` bruto (LGPD) nem hashes completos além do necessário.
- **Métricas** (Micrometer): `hist_audit_fact_recorded_total{factType}`, `hist_duplicate_fact_ignored_total`,
  `hist_correction_recorded_total`, `hist_chain_verification_total{status}`,
  `hist_ingestion_latency_seconds`, `hist_acs_scope_resolution_total{outcome}`.
- **Health**: `/actuator/health` (liveness), `/actuator/health/readiness` (DB + ACS JWK reachable). ACS
  directory indisponível **não** derruba readiness (só afeta leituras de cuidadora).
- **Correlação**: `X-Correlation-Id` gerado no GWY se ausente; propagado a chamadas ACS.

## Critérios de aceite (EARS)

- WHEN any request is processed, THE SYSTEM SHALL emit a structured JSON log line carrying the
  `correlationId` and the acting `tenant`.
- THE SYSTEM SHALL NOT log raw `payload` content nor biometric-adjacent fields.
- WHEN an append succeeds, THE SYSTEM SHALL increment `hist_audit_fact_recorded_total{factType}`.
- WHEN a duplicate `factId` is ignored, THE SYSTEM SHALL increment `hist_duplicate_fact_ignored_total`.
- WHEN a chain verification completes, THE SYSTEM SHALL increment
  `hist_chain_verification_total{status=intact|broken|empty}`.
- WHILE PostgreSQL is unreachable, THE SYSTEM SHALL report readiness `DOWN`.
- WHILE the ACS directory is unreachable, THE SYSTEM SHALL keep readiness `UP` and SHALL degrade only
  caregiver-scoped reads (SPEC-03).
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate one and propagate it to outbound ACS
  calls.
