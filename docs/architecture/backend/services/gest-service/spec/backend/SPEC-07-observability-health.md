# SPEC-07 — Observability and Health (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Define the observability posture of `gest-service`: structured logging, metrics, distributed
tracing, and health/readiness probes. Ensures the service is monitorable in production without
exposing LGPD-sensitive data.

## 2. Scope

**In:**
- Structured log fields (JSON, ECS-compatible)
- Spring Boot Actuator: health, readiness, liveness
- Micrometer metrics (Prometheus endpoint)
- Distributed tracing (Micrometer Tracing / OpenTelemetry)
- Log/metric data privacy rules

**Out:**
- Dashboarding layout (DASH module)
- Alerting rules (infra scope)

## 3. Health endpoints

| Path | Purpose | Auth required |
|---|---|---|
| `GET /actuator/health` | Kubernetes liveness + readiness probe | No (unauthenticated) |
| `GET /actuator/health/readiness` | Ready to serve traffic (DB connection up, schema resolved) | No |
| `GET /actuator/info` | Service version, build info | No |

- THE SYSTEM SHALL respond `200 UP` on `/actuator/health` when the PostgreSQL connection pool
  and schema routing are operational.
- THE SYSTEM SHALL respond `503 OUT_OF_SERVICE` on `/actuator/health` if the DB is unreachable,
  allowing the orchestrator/GWY to remove the instance from rotation.
- WHILE the service is starting (before schema routing is ready), THE SYSTEM SHALL respond `503`
  on the readiness probe and `200` on the liveness probe.

## 4. Structured logging

Log format: JSON (Logback + logstash-logback-encoder). Every line includes:

| Field | Value |
|---|---|
| `@timestamp` | ISO-8601 UTC |
| `level` | `INFO` / `WARN` / `ERROR` |
| `service` | `gest-service` |
| `correlationId` | from `X-Correlation-Id` request header |
| `tenantId` | SHA-256 hash of the raw tenant claim (LGPD: never raw) |
| `method` | HTTP verb |
| `path` | URI path (no query string) |
| `status` | HTTP status code |
| `durationMs` | request duration |

- THE SYSTEM SHALL NOT log any video, image, landmark, biometric datum, confidence value, or
  patient-identifying data in any log field.
- THE SYSTEM SHALL log at `WARN` level any illegal transition attempt with the `TipoGesto` id
  and the disallowed `toState` (but not the actor PII beyond `tenantId` hash).
- THE SYSTEM SHALL log at `ERROR` level any unhandled exception with `correlationId`.

## 5. Metrics

Exposed at `GET /actuator/prometheus` (Micrometer Prometheus registry).

| Metric | Type | Labels |
|---|---|---|
| `gest.tipo_gesto.registered.total` | Counter | `tenant_hash` |
| `gest.tipo_gesto.transitions.total` | Counter | `from_state`, `to_state`, `tenant_hash` |
| `gest.profile.published.total` | Counter | `tenant_hash` |
| `gest.eligibility.check.total` | Counter | `urgencia`, `eligible`, `tenant_hash` |
| `gest.report.validated.total` | Counter | `valid`, `tenant_hash` |
| `http.server.requests` | Timer | `method`, `uri`, `status` (Spring default) |

- THE SYSTEM SHALL NOT include raw tenant ids or patient ids as metric labels; use `tenant_hash`.

## 6. Distributed tracing

- THE SYSTEM SHALL propagate the `X-Correlation-Id` header as the trace span identifier.
- WHEN calling downstream services (EDGE for device pairing), THE SYSTEM SHALL forward the
  `X-Correlation-Id` header.
- Spans SHALL include: `tenantId` (hashed), `operationName`, `http.method`, `http.status_code`.

## 7. Acceptance criteria

- `/actuator/health` returns `200 UP` with a healthy PostgreSQL connection.
- `/actuator/health` returns `503` when the DB is unreachable.
- Every access log line contains `correlationId` and `tenantId` (hashed).
- No log line or metric label contains a raw tenant id, patient id, or confidence value.
- Prometheus scrape at `/actuator/prometheus` exposes `gest.*` counters without error.
