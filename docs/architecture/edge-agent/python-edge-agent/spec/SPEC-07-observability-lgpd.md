# SPEC-07 — Observability, Logging, and LGPD Constraints

**Component:** PEA | **Date:** 2026-07-30

---

## 1. Structured logging

- THE SYSTEM SHALL use `structlog` for all log output, emitting JSON lines to stdout.
- THE SYSTEM SHALL include in every log event: `timestamp` (ISO-8601), `level`,
  `component` (e.g. `capture`, `classifier`, `event_publisher`), `deviceId`,
  and `correlationId` (= session correlation id).
- THE SYSTEM SHALL respect the log level configured at `logging.level` in
  `config.toml` (default `INFO`).

## 2. LGPD field blocklist

- THE SYSTEM SHALL apply a `PrivacyFilter` processor in the structlog pipeline
  that removes any key matching the blocklist before output:
  - `landmarks`, `landmark_coordinates`, `face_data`, `frame`, `image`, `pixel`,
    `biometric`, `coordinates`, `lm`, `points`
  - Any key whose value is a list of ≥ 100 numbers (heuristic: raw landmark arrays)
- WHEN a field in the blocklist is detected, THE SYSTEM SHALL replace its value
  with `"[REDACTED-LGPD]"` and add `lgpd_redacted: true` to the log event.
- THE SYSTEM SHALL NOT log `client_secret`, JWT payload, or token value at any level.

## 3. LGPD invariants (pipeline enforcement)

- THE SYSTEM SHALL NOT write any `LandmarkSet` to any persistent store (SQLite,
  file system, network) — in-memory only, discarded after classification.
- THE SYSTEM SHALL NOT include facial or biometric data in any event payload
  published to EVNT (payload = `deviceId`, `patientId`, `comandoId`, `detectedAt`,
  `confidence`, `eventId`).
- WHEN a `LandmarkSet` is enqueued to the asyncio queue for longer than 500ms
  without being consumed, THE SYSTEM SHALL discard it without logging coordinates.

## 4. Operational metrics (lightweight)

- THE SYSTEM SHALL maintain in-memory counters (reset on restart):
  - `frames_captured_total`
  - `faces_detected_total`
  - `commands_matched_total`
  - `events_published_total`
  - `events_failed_total`
  - `auth_refreshes_total`
- THE SYSTEM SHALL expose these counters via a local HTTP endpoint
  `GET http://127.0.0.1:{metrics.port}/metrics` (default port 9090) as
  Prometheus text format.
- THE SYSTEM SHALL NOT expose the metrics endpoint on any interface other than
  `127.0.0.1`.

## 5. Error reporting

- WHEN an unhandled exception propagates to the top-level asyncio event loop,
  THE SYSTEM SHALL log CRITICAL with the exception type and message (NOT traceback
  variables that may contain biometric data) and exit with code 1.
- THE SYSTEM SHALL catch and log WARN for all transient errors (network timeouts,
  HTTP 5xx, ONNX inference errors) without exiting.
- THE SYSTEM SHALL log INFO at startup summarizing: `deviceId`, `agentVersion`,
  `modelVersion`, `patientId`, `commandCount`, `backendUrl`.
