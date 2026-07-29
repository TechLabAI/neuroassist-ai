# SPEC-08 — BDD Test Scenarios Summary

**Component:** PEA | **Date:** 2026-07-30

---

## Overview

BDD scenarios for the PEA are organized into feature files under
`tests/features/`. Each feature targets one behavioral boundary. Scenarios use
Gherkin syntax with `@tag` annotations for grouping.

## Feature index

| Feature file | Covers | SPEC ref |
|---|---|---|
| `capture_pipeline.feature` | Camera init, frame capture, MediaPipe detection | SPEC-01 |
| `classification_matching.feature` | ONNX inference, FSM states, compound commands, debounce | SPEC-02 |
| `event_publication.feature` | Outbox enqueue, drain, back-off, idempotency | SPEC-03 |
| `authentication.feature` | Token grant, refresh, DEGRADED_AUTH mode | SPEC-04 |
| `vocabulary_management.feature` | GEST profile load, PAC command load, cache refresh, hot-swap | SPEC-05 |
| `enrollment_heartbeat.feature` | EDGE enrollment, heartbeat, re-enrollment on 404 | SPEC-06 |

## Key scenario titles

### capture_pipeline.feature
- `Camera opens on configured device index`
- `Frames are dropped when queue is full without blocking capture`
- `MediaPipe produces LandmarkSet on face detection`
- `No LandmarkSet produced when no face in frame`
- `No frame data written to disk under any condition`

### classification_matching.feature
- `ONNX classifies gesture primitive with expected confidence`
- `FSM transitions from IDLE to TRACKING_PRIMARY on sustained detection`
- `FSM resets to IDLE when confidence drops below threshold`
- `Simple command fires CommandMatch after tempoMinGestoMs`
- `Compound command fires CommandMatch when secondary follows within 500ms`
- `Cooldown prevents duplicate match for tempoMaxGestoMs + 200ms`
- `End-to-end latency stays under 200ms on host machine`

### event_publication.feature
- `GestoDetectado event is inserted to outbox on CommandMatch`
- `Outbox drain delivers event to EVNT with idempotency key`
- `409 response from EVNT deletes outbox row (idempotent)`
- `Back-off schedule delays retry after 5xx from EVNT`
- `Outbox overflow drops oldest row and logs ALERT`
- `Outbox is replayed on PEA restart`
- `Shutdown drains outbox for up to 5 seconds`

### authentication.feature
- `Token obtained from ACS on startup with client_credentials`
- `Cached token returned without network call while not expired`
- `New token fetched when cached token expires`
- `DEGRADED_AUTH entered after two consecutive 401 responses`
- `DEGRADED_AUTH exits and draining resumes after token recovery`
- `client_secret never appears in log output`

### vocabulary_management.feature
- `Detection profile loaded from GEST on startup`
- `Cached profile used when GEST unreachable on startup`
- `Command vocabulary loaded from PAC using patientId from profile`
- `Command cache invalidated and refreshed on EVNT 422 unknown-command`
- `New ONNX model hot-swapped when modelVersion changes`

### enrollment_heartbeat.feature
- `Device enrolled via POST when not found in EDGE`
- `409 on enrollment treated as already enrolled`
- `Heartbeat sent every interval with correct status fields`
- `Re-enrollment triggered when heartbeat returns 404`
- `Final heartbeat with status=stopped sent on graceful shutdown`
