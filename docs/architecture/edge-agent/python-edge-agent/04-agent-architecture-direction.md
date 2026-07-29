# PEA — Agent Architecture Direction

**Component:** Python Edge Agent | **Date:** 2026-07-29

> Note: This doc adapts the standard "backend architecture direction" template for a Python edge process.
> The naming "agent architecture direction" reflects that the PEA is not a Java Spring Boot service.

---

## 1. Architecture style

**Async pipeline with local SQLite persistence.**

The PEA is a **single-process, two-thread async pipeline**:
- One OS thread handles blocking webcam I/O (OpenCV).
- The main thread runs an asyncio event loop for all other work.
- Persistence is local SQLite (not PostgreSQL — the PEA has no access to the shared DB).
- No broker, no message queue, no REST server. The PEA is purely a producer/consumer.

This matches the co-located deployment (all on the same machine) and the real-time latency constraint.

---

## 2. Design principles

| Principle | Application |
|---|---|
| **Fail-fast on startup** | Auth, profile, vocabulary, and model load block startup. A missing dependency surfaces immediately, not at first detection. |
| **Fail-silent on runtime** | After startup, individual failures (EVNT down, PAC stale cache) degrade gracefully — detection continues, events queue locally. |
| **Never block the detection loop** | HTTP calls, SQLite writes, and cache refreshes happen asynchronously. The frame pipeline is never stalled by I/O. |
| **Idempotent events** | Every `GestoDetectado` has a stable `eventId` (UUID v4, generated once, reused on retry). |
| **Privacy by construction** | No frame, image, or landmark leaves the processing pipeline. The LGPD constraint is a code invariant, not a policy. |
| **Explicit over implicit** | Config is validated at startup with clear error messages. No silent defaults that cause hard-to-debug runtime failures. |

---

## 3. Package structure

```
src/neuroassist_edge/
├── __init__.py
├── main.py               # entry point: argparse, asyncio.run(main()), signal handlers
├── config.py             # ConfigLoader + Config dataclass (validated at startup)
├── startup.py            # BackendHealthCheck + ordered startup sequence
│
├── capture/
│   ├── capture.py        # CaptureThread (OpenCV, frames → queue)
│   └── landmarks.py      # LandmarkExtractor (MediaPipe FaceMesh)
│
├── detection/
│   ├── classifier.py     # GestureClassifier (ONNX Runtime)
│   ├── matcher.py        # CommandMatcher (GestureWindow FSM)
│   └── models.py         # ClassificationResult, CommandMatch, GestureWindow dataclasses
│
├── cache/
│   ├── profile.py        # DetectionProfileCache (GEST API + SQLite)
│   ├── commands.py       # CommandCache (PAC API + SQLite)
│   └── db.py             # SQLite init + table creation (aiosqlite)
│
├── publisher/
│   ├── publisher.py      # EventPublisher (enqueue + drain loop)
│   └── outbox.py         # OutboxEntry CRUD (aiosqlite)
│
├── auth/
│   └── auth.py           # AuthClient (ACS client_credentials, in-memory token cache)
│
├── edge/
│   ├── heartbeat.py      # HeartbeatReporter (EDGE service, 30s)
│   └── enrollment.py     # Enrollment CLI command
│
└── observability/
    └── logging.py        # structlog configuration + LGPD field blocklist
```

---

## 4. Dependency rules

```
main.py
  └── startup.py
        ├── auth/auth.py
        ├── cache/profile.py  (depends on: auth, cache/db)
        ├── cache/commands.py (depends on: auth, cache/db)
        └── detection/classifier.py

capture/ → detection/ (via asyncio.Queue)
detection/matcher.py → cache/profile.py, cache/commands.py (read-only)
detection/matcher.py → publisher/publisher.py (on CommandMatch)
publisher/ → auth/ (for Bearer token)
edge/heartbeat.py → auth/, publisher/ (outbox depth)
```

**Hard rules:**
- `capture/` and `detection/` have no HTTP calls — pure computation
- `cache/` handles all GEST/PAC HTTP fetching; detection reads caches read-only
- `publisher/` is the only module that calls EVNT
- `auth/` is the only module that calls ACS

---

## 5. State machine — CommandMatcher (per Comando)

```
IDLE
  │ primitive detected with confidence ≥ threshold
  ▼
TRACKING_PRIMARY
  │ held for < tempoMinGestoMs → stay
  │ confidence drops below threshold → IDLE (reset)
  │ held ≥ tempoMinGestoMs AND ≤ tempoMaxGestoMs:
  │   if Comando is simple → MATCHED
  │   if Comando is compound → TRACKING_SECONDARY
  │ held > tempoMaxGestoMs → IDLE (too long, reset)
  ▼
TRACKING_SECONDARY (compound commands only)
  │ within 500ms: secondaryPrimitive detected with confidence ≥ threshold → MATCHED
  │ timeout (> 500ms) or wrong primitive → IDLE
  ▼
MATCHED
  │ enqueue GestoDetectado → COOLDOWN
  ▼
COOLDOWN (duration: tempoMaxGestoMs + 200ms)
  │ timer expires → IDLE
```

Each `Comando` in the vocabulary has its own independent state machine instance.

---

## 6. Error taxonomy

| Category | Examples | Response |
|---|---|---|
| **Startup-fatal** | ACS unreachable, GEST 403, model file missing | Log CRITICAL + exit(1) |
| **Runtime-degraded** | PAC cache stale, EVNT 5xx, heartbeat failed | Log WARN, continue detection |
| **Privacy-violation** | Any attempt to log landmarks or frame data | Blocked by structlog field blocklist; test-verified |
| **Outbox-overflow** | > 50 pending events | Log ALERT + drop oldest; continue |
| **Auth-expired** | JWT expired AND ACS unreachable | Log CRITICAL, publish AUTH_UNAVAILABLE event to observability, stop new events (detection continues, events not queued) |

---

## 7. Configuration schema

```toml
[backend]
url = "http://localhost:8080"   # GWY base URL (all services via GWY)
# or individual ports if bypassing GWY:
# acs_url = "http://localhost:8088"
# gest_url = "http://localhost:8084"
# evnt_url = "http://localhost:8086"
# pac_url  = "http://localhost:8083"
# edge_url = "http://localhost:8089"

[device]
id         = ""        # set by enrollment
patient_id = ""        # set by enrollment
tenant_id  = ""        # set by enrollment

[capture]
device_index = 0
fps          = 15
resolution   = "640x480"
model_path   = "./models/gesture_v1.onnx"

[logging]
level  = "INFO"        # DEBUG | INFO | WARN | ERROR | CRITICAL
format = "json"        # json | pretty
```
