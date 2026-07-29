# SPEC-06 — Device Enrollment and Heartbeat

**Component:** PEA | **Date:** 2026-07-30

---

## 1. Enrollment check

- WHEN the PEA starts (after token obtained), THE SYSTEM SHALL call
  `GET /edge/devices/{deviceId}` to check whether the device is registered.
- WHEN EDGE returns `200 OK`, THE SYSTEM SHALL proceed with normal startup.
- WHEN EDGE returns `404 Not Found`, THE SYSTEM SHALL call
  `POST /edge/devices` with the enrollment payload and register the device.
- WHEN enrollment (`POST /edge/devices`) returns `201 Created`, THE SYSTEM SHALL
  log INFO `device enrolled` and proceed.
- WHEN enrollment returns `409 Conflict`, THE SYSTEM SHALL treat the device as
  already enrolled and proceed.
- WHEN enrollment returns any other non-2xx response, THE SYSTEM SHALL log
  CRITICAL and exit with code 1.

## 2. Enrollment payload

```json
{
  "deviceId": "{device.id}",
  "patientId": "{resolved from detection-profile}",
  "hostname": "{socket.gethostname()}",
  "agentVersion": "{PEA.__version__}",
  "modelVersion": "{detection-contract.modelVersion}"
}
```

## 3. Heartbeat

- THE SYSTEM SHALL send `POST /edge/devices/{deviceId}/heartbeat` every
  `{heartbeat.interval_seconds}` seconds (default 30).
- THE SYSTEM SHALL include in the heartbeat body:
  - `status`: `running` | `degraded_auth` | `degraded_vision`
  - `uptimeSeconds`: seconds since PEA startup
  - `queueDepth`: current `event_outbox` row count
  - `lastMatchAt`: ISO-8601 timestamp of last `CommandMatch` (null if none yet)
- WHEN EDGE returns `200 OK`, THE SYSTEM SHALL log DEBUG `heartbeat ok`.
- WHEN EDGE returns `404 Not Found` (device was deregistered), THE SYSTEM SHALL
  log WARN and re-trigger the enrollment flow.
- WHEN EDGE returns `5xx` or is unreachable, THE SYSTEM SHALL log WARN and retry
  on the next scheduled heartbeat; do NOT exit.
- THE SYSTEM SHALL send a final heartbeat with `status: stopped` as part of the
  graceful shutdown sequence before exiting.

## 4. Status transitions reported via heartbeat

| PEA internal state | `status` in heartbeat |
|---|---|
| All subsystems running normally | `running` |
| `DEGRADED_AUTH` mode (see SPEC-04 §4) | `degraded_auth` |
| Camera unavailable (SIGPIPE / capture error) | `degraded_vision` |
| Shutting down | `stopped` (final heartbeat only) |
