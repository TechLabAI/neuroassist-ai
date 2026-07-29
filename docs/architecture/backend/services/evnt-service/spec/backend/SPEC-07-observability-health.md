# SPEC-07 — Observability + Health

> Service: `evnt-service` · The alarm path is safety-critical; every emergency must be traceable.

## Objetivo
Specify health/readiness, metrics, structured logging, and correlation so that "no emergency is silently
lost" is observable, not just intended.

## Escopo
- **In:** `/health` liveness/readiness, correlation propagation, metrics for the alarm/dispatch path,
  structured logs, HIST audit mirroring.
- **Out:** HIST's storage (owned by HIST), dashboards (DASH).

## Decisões técnicas
- **Health:** Spring Actuator; readiness gates on DB (tenant datasource) and Eureka registration. GEST/PAC/
  ACS/DASH/HIST are **not** readiness gates (their outages are handled by fail-open/fail-closed policy, not
  by taking evnt-service out of rotation).
- **Correlation:** `X-Correlation-Id` on every request, log line, downstream call, and audit fact.
- **Metrics (Micrometer):** `evnt.reports.ingested` (tags: urgencia, outcome), `evnt.evento.created`,
  `evnt.dispatch.attempts`, `evnt.dispatch.failed`, `evnt.dispatch.retry.sweep`,
  `evnt.emergency.time_to_dispatch` (histogram), `evnt.confirmations` (tags: verdict),
  `evnt.stream.connections` (gauge).
- **Alerting signals:** any `notification_dispatch` for an `emergencia` stuck in `queued`/`failed` beyond a
  threshold is the top operational alert.
- **Audit mirror:** `EventoCriado`/`EventoConfirmado`/`FalsoPositivoMarcado`/dispatch-failures appended to HIST.

## Casos de borda
- HIST append failure → retried off the critical path; logged; never blocks the alarm.
- Retry sweep re-queuing on boot → counted via `evnt.dispatch.retry.sweep`.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL expose `/health` with liveness and readiness; readiness SHALL gate on the tenant
  datasource and Eureka registration only.
- THE SYSTEM SHALL attach `X-Correlation-Id` to every log line, downstream call, and audit fact for a request.
- WHEN an `emergencia` dispatch fails, THE SYSTEM SHALL increment `evnt.dispatch.failed` and keep the
  dispatch inspectable (status `failed`/`queued`) until resolved.
- THE SYSTEM SHALL record `evnt.emergency.time_to_dispatch` for every emergency Evento.
- IF a HIST audit append fails, THEN THE SYSTEM SHALL retry it off the critical path without blocking the
  notification.
