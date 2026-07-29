# PEA — Architecture Blueprint

**Component:** Python Edge Agent | **Date:** 2026-07-29

---

## 1. Deployment topology — co-located on patient's machine

**Key constraint (confirmed 2026-07-29):** The PEA and all NeuroAssist AI Java services run on the
**same physical machine** (server/PC running the Java services; MVP uses integrated or USB webcam). They are separate processes but
co-located. Communication is HTTP over loopback (localhost), not internet HTTPS.

```
Patient's Machine
┌──────────────────────────────────────────────────────────────────┐
│                                                                   │
│  USB Webcam ──▶ [neuroassist-edge-agent] (Python 3.11 process)  │
│                         │ HTTP localhost                          │
│                         ▼                                         │
│  ┌──────────────────────────────────────────────────────────┐    │
│  │  NeuroAssist AI Backend (Java services, same machine)    │    │
│  │  ┌─────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ │    │
│  │  │ GWY │ │ ACS  │ │ GEST │ │ EVNT │ │ PAC  │ │ EDGE │ │    │
│  │  │8080 │ │8088  │ │8084  │ │8086  │ │8083  │ │8089  │ │    │
│  │  └─────┘ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘ │    │
│  │  PostgreSQL (localhost:5432)    Eureka (localhost:8761)  │    │
│  └──────────────────────────────────────────────────────────┘    │
│                                                                   │
│  Caregiver Browser ──▶ http://localhost:8080 (GWY)              │
└──────────────────────────────────────────────────────────────────┘
```

**Implications:**
- Communication: `http://localhost:{port}` — TLS not required for intra-machine calls
- Latency: loopback RTT < 1ms; end-to-end gesture→event < 200ms easily achievable
- LGPD: video/biometric data cannot leave the machine by construction (no network egress)
- "Offline" means Java services restarting or not yet started — not network outage
- Startup order: Java services must be up before PEA can initialize (health check loop)
- The outbox pattern provides resilience against individual Java service restarts

---

## 2. C4 Container (within the machine)

```
[neuroassist-edge-agent process]
┌─────────────────────────────────────────────────────────────────┐
│                                                                  │
│  [Thread] CaptureThread          [AsyncIO Event Loop]            │
│  ┌──────────────────┐            ┌─────────────────────────────┐│
│  │ OpenCV           │──frames──▶ │ LandmarkExtractor           ││
│  │ VideoCapture     │            │ (MediaPipe FaceMesh)        ││
│  │ (15 fps)         │            └──────────┬──────────────────┘│
│  └──────────────────┘                       │ LandmarkSet        │
│                                             ▼                    │
│                                   ┌─────────────────────────────┐│
│                                   │ GestureClassifier           ││
│                                   │ (ONNX Runtime, CPU)         ││
│                                   └──────────┬──────────────────┘│
│                                              │ ClassificationResult│
│                                             ▼                    │
│                            ┌──────────────────────────────────── │
│  ┌──────────────────┐      │ CommandMatcher                     ││
│  │ DetectionProfile │─────▶│ (timing + debounce FSM)            ││
│  │ Cache (GEST)     │      └──────────┬──────────────────────── ││
│  └──────────────────┘                 │ CommandMatch              │
│  ┌──────────────────┐                 ▼                          │
│  │ CommandCache     │       ┌─────────────────────────────────┐  │
│  │ (PAC)            │──────▶│ EventPublisher                  │  │
│  └──────────────────┘       │ (outbox + retry)                │  │
│                             └──────────┬────────────────────── │  │
│                                        │ HTTP POST              │  │
│                                        ▼ localhost:8086/evnt    │  │
│                                                                  │
│  ┌──────────┐  ┌────────────────┐  ┌──────────────────────┐    │
│  │AuthClient│  │HeartbeatReporter│  │ ConfigLoader         │    │
│  │(ACS JWT) │  │(EDGE 30s)      │  │ (env > .toml)        │    │
│  └──────────┘  └────────────────┘  └──────────────────────┘    │
│                                                                  │
│  [SQLite ~/.neuroassist/local.db]                                │
│  ┌─────────────────┐  ┌──────────────────┐  ┌────────────────┐  │
│  │ event_outbox    │  │detection_profile  │  │command_cache   │  │
│  │ (≤50 rows)      │  │_cache             │  │                │  │
│  └─────────────────┘  └──────────────────┘  └────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

---

## 3. Threading and concurrency model

| Thread | Role | Communication |
|---|---|---|
| `CaptureThread` | Blocking OpenCV `VideoCapture.read()` loop at 15 fps | `asyncio.Queue(maxsize=5)` → event loop |
| `AsyncIO Event Loop` (main thread) | Landmark extraction, classification, matching, HTTP, SQLite | Internal coroutines + queues |

**Why two threads:** `VideoCapture.read()` is a blocking C call that cannot run inside asyncio. The capture thread feeds frames via a queue and drops overflow (back-pressure without blocking).

**Why asyncio for the rest:** httpx async, aiosqlite, and asyncio timers allow cooperative multitasking without thread locks on shared state.

---

## 4. Data flow

```
Webcam frame (BGR)
  │ [CaptureThread → frame_queue]
  ▼
LandmarkSet (468 face landmarks, normalized coords)
  │ [MediaPipe FaceMesh, ~10ms on host CPU]
  ▼
ClassificationResult (primitive_code, confidence)
  │ [ONNX model, ~5ms on host CPU]
  ▼
CommandMatch? (comandoId, urgency, durationMs)
  │ [CommandMatcher FSM: timing + debounce + threshold]
  ▼ (on match)
event_outbox row (SQLite INSERT)
  │ [drain loop, async]
  ▼
POST http://localhost:8086/api/v1/evnt/events
  │
  ├─ 202 → DELETE from outbox
  ├─ 409 → DELETE (idempotent)
  ├─ 422 → DELETE + CommandCache.invalidate()
  └─ 5xx/timeout → retain, back-off retry
```

---

## 5. Startup sequence

```
1. ConfigLoader.load()              ← env > ~/.neuroassist/config.toml > ./config.toml
2. SQLite.init()                    ← create tables if not exist; replay pending outbox
3. BackendHealthCheck.wait()        ← poll http://localhost:8080/actuator/health until UP
                                       timeout 60s; fail with BACKEND_UNAVAILABLE
4. AuthClient.get_token()           ← POST http://localhost:8088/oauth2/token (client_credentials)
5. DetectionProfileCache.fetch()    ← GET http://localhost:8084/api/v1/gest/devices/{id}/detection-profile
6. CommandCache.fetch()             ← GET http://localhost:8083/api/v1/pac/patients/{id}/commands/active
                                       [endpoint pending PAC Layer 1]
7. GestureClassifier.load_model()  ← load ONNX from config.capture.model_path
8. CaptureThread.start()
9. HeartbeatReporter.start()        ← asyncio task, 30s interval
10. EventPublisher.drain_loop()     ← asyncio task, drains outbox from previous session
11. Main asyncio event loop running
```

Steps 3–7 are blocking fail-fast. The backend health check (step 3) ensures the Java services
are available before the PEA tries to authenticate or fetch profiles.

---

## 6. Shutdown sequence (SIGTERM / SIGINT)

```
1. CaptureThread.stop()            ← signal + join(timeout=2s)
2. EventPublisher.drain(timeout=5s) ← final flush of outbox
3. HeartbeatReporter.send_offline() ← POST heartbeat with status="offline"
4. SQLite.close()
5. exit(0)
```
