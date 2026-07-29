# SPEC-07 — Observability & Health (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge
> **ADR refs:** ADR-006 (liveness is the safety-critical signal)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify health/readiness probes, metrics, structured logging, and correlation for `edge-service`, with
emphasis on the safety-critical signals: heartbeat ingestion, liveness transitions, and profile drift.

## 2. Scope

**In:**
- Spring Boot Actuator health/readiness/liveness probes
- Micrometer metrics (heartbeat rate, online/offline gauges, drift, GEST degradation)
- Structured logging with `X-Correlation-Id`; audit distinction
- Config surface for the operational parameters (SPEC-02 §4)

**Out:**
- Central log aggregation infra (platform); alerting rules (DASH/ops)

## 3. Health & readiness (EARS)

- THE SYSTEM SHALL expose `/actuator/health` (liveness) and `/actuator/health/readiness` unauthenticated.
- THE SYSTEM SHALL report `readiness = DOWN` WHILE the tenant datasource or REG registration is
  unavailable, so GWY stops routing until recovery.
- THE SYSTEM SHALL report `health = UP` independently of GEST availability — GEST being down degrades the
  profile pointer but not EDGE liveness (integration-contracts §1.2).

## 4. Metrics (EARS)

- THE SYSTEM SHALL emit `edge.heartbeat.received` (counter, tags: `tenant` hashed) on each accepted beat.
- THE SYSTEM SHALL emit `edge.devices.online` / `edge.devices.offline` (gauges, per tenant) derived from
  the liveness policy, so ops can watch fleet health.
- THE SYSTEM SHALL emit `edge.profile.drift` (counter) when `profileDrift` is detected.
- THE SYSTEM SHALL emit `edge.gest.degraded` (counter) when a heartbeat completes with
  `profilePointerDegraded: true`.
- THE SYSTEM SHALL expose `edge.staleness.threshold.seconds` as a gauge reflecting the effective config.

## 5. Logging & correlation (EARS)

- THE SYSTEM SHALL include `X-Correlation-Id` in every structured log line and propagate it to GEST
  (integration-contracts §1.2).
- WHEN a liveness crossing (`online↔offline`) is observed, THE SYSTEM SHALL log it at INFO with
  `deviceId`, `pacienteId`, and `ultimaSincronizacao` — a safety-relevant event (doc 02 Observability).
- THE SYSTEM SHALL NOT log credential secrets, enrollment tokens, or any biometric-adjacent data.
- THE SYSTEM SHALL treat the `dispositivo_edge_transition` table as the durable audit; logs are
  operational, the table is evidence (SPEC-03/SPEC-06).

## 6. Configuration surface

| Config key | Default | Spec |
|---|---|---|
| `neuroassist.edge.heartbeat.interval-seconds` | `30` | SPEC-02 |
| `neuroassist.edge.heartbeat.staleness-factor` | `3` | SPEC-02 |
| `neuroassist.edge.liveness.poll-hint-seconds` | `15` | SPEC-02 |
| `neuroassist.edge.enrollment.ttl-seconds` | `900` | SPEC-06/11 |

## 7. Acceptance criteria

- Health/readiness probes respond without auth and reflect datasource/REG state.
- GEST down does not turn EDGE health `DOWN`; `edge.gest.degraded` increments instead.
- Every request and every liveness crossing carries the same `X-Correlation-Id` end to end.
- No secret/token/biometric value appears in metrics labels or logs.

## 8. Notes on proactive alerting (deferred)

Proactive "device went offline" without a reader requires a periodic sweep — deliberately deferred to
avoid a scheduler dependency in the MVP (ADR-006, doc 06). When added, it reuses the liveness policy and
the `DispositivoFicouOffline` fact; the metrics/log shapes here already anticipate it.
