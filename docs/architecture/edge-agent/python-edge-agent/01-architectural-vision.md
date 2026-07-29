# Python Edge Agent (PEA) — Architectural Vision

**Component:** `neuroassist-edge-agent` (separate repository)
**Decision:** D-534 | **Issue:** EDGE-PY-001
**Date:** 2026-07-29
**Status:** Approved — Layer 1

---

## 1. Purpose

The Python Edge Agent (PEA) is the local process that runs on the patient's device and is the system's
primary sensory component. It:

1. Captures frames from a standard USB/built-in webcam
2. Extracts facial/hand landmarks using a lightweight ML model
3. Classifies the detected primitive gesture(s) against the patient's active vocabulary
4. Maps gesture(s) + timing to a concrete `Comando` (with debounce logic)
5. Publishes a `GestoDetectado` event to the `evnt-service` via authenticated REST

Without the PEA running on the patient's device, no alarm or help request can be generated.
It is the entry point of the entire NeuroAssist AI signal chain.

---

## 2. Deployment topology

```
Host machine — same server/PC where Java services run (MVP: integrated or USB webcam)
┌─────────────────────────────────────────────────────────┐
│  neuroassist-edge-agent (Python 3.11+ process)          │
│                                                         │
│  ┌──────────────┐   ┌────────────────┐                  │
│  │CaptureModule │──▶│LandmarkExtract │                  │
│  │(OpenCV loop) │   │(MediaPipe)     │                  │
│  └──────────────┘   └───────┬────────┘                  │
│                             │ landmarks                  │
│                    ┌────────▼────────┐                  │
│                    │GestureClassifier│ (ONNX model)     │
│                    └────────┬────────┘                  │
│                             │ primitive gesture          │
│                    ┌────────▼────────┐                  │
│                    │CommandMatcher   │◀── VocabularyCache│
│                    │(timing+debounce)│    (GEST API)     │
│                    └────────┬────────┘                  │
│                             │ Comando matched            │
│                    ┌────────▼────────┐                  │
│                    │EventPublisher   │──▶ evnt-service   │
│                    └─────────────────┘   (HTTP localhost)│
│                                                         │
│  AuthClient (ACS client_credentials) ───▶ acs-service   │
│  HeartbeatReporter ──────────────────▶ edge-service     │
└─────────────────────────────────────────────────────────┘
```

**Communication:** HTTP localhost only — PEA and Java services are co-located on the same machine.
**"Offline" behavior:** Vocabulary is cached locally; events queue in SQLite outbox and drain
when the Java services restart (max 50 events, FIFO). "Offline" = Java services stopped, not
network outage.

---

## 3. Core components

### 3.1 CaptureModule

Manages the OpenCV `VideoCapture` loop. Runs in a dedicated thread at a configurable FPS
(default 15 fps). Publishes frames to an in-process queue consumed by `LandmarkExtractor`.

Configuration:
- `capture.device_index` (default 0)
- `capture.fps` (default 15)
- `capture.resolution` (default 640×480)

### 3.2 LandmarkExtractor

Uses **MediaPipe FaceMesh** (468 landmarks) + optionally **MediaPipe Hands** (21 landmarks per hand)
to extract 2D/3D landmark coordinates from each frame. Outputs a `LandmarkSet` proto.

Rationale for MediaPipe: runs on CPU (no GPU required on edge device), well-maintained Python API,
low latency (~10ms per frame on host machine CPU).

### 3.3 GestureClassifier

A small ML classifier (ONNX format, < 5 MB) that maps a `LandmarkSet` → `TipoGesto` (primitive gesture
label). The model is bundled with the agent and updated via the GEST vocabulary sync.

Initial gesture primitives (from BA data model — 7 TipoGestos):
- The active set is determined by the patient's vocabulary; classifier maps to primitive IDs.

### 3.4 CommandMatcher

Matches the current detected primitive (or sequence of two primitives for compound commands) to a
`Comando` in the patient's active vocabulary.

Matching rules (from BA model — `Comando` fields):
- `primitivoPrincipal` must match continuously for [`tempoMinGesto` .. `tempoMaxGesto`] ms
- `primitivoSecundario` (if set) must follow within a configurable window (default 500ms)
- `sensibilidade` maps to a confidence threshold applied to the classifier score
- Debounce: once a `Comando` is matched and published, a cooldown of `tempoMaxGesto + 200ms` applies
  before the same command can be matched again

### 3.5 VocabularyCache

Fetches the patient's active vocabulary from `GET /api/v1/gest/vocabulary/{pacienteId}/active`
(GEST service). Caches the response locally (SQLite, `cache/vocabulary.db`).

Refresh strategy:
- On startup (blocking, fails fast if GEST unreachable and no cache exists)
- Every 5 minutes (background thread, non-blocking)
- On `HTTP 410 Gone` from EventPublisher (vocabulary stale signal)
- On manual SIGHUP

### 3.6 EventPublisher

Posts a `GestoDetectado` event to `POST /api/v1/evnt/events` (EVNT service).

- Uses the JWT obtained by `AuthClient` (passed as `Authorization: Bearer <token>`)
- On success (HTTP 202): removes event from outbox
- On failure (network error, 5xx): retains in local SQLite outbox, retries with exponential back-off
  (1s, 5s, 30s, 2m, 10m)
- Outbox capacity: 50 events (FIFO; oldest dropped if full — logged as `ALERT`)
- On `HTTP 401`: triggers token refresh via `AuthClient` (one attempt before fail)

### 3.7 AuthClient

Obtains a JWT from ACS using the OAuth2 `client_credentials` grant:

```
POST /oauth2/token
  grant_type=client_credentials
  client_id=<device_id>
  client_secret=<device_secret>
  scope=edge:publish
```

- Token cached in memory; refreshed 60s before expiry (TTL 900s → refresh at 840s)
- `client_id` = device registration ID (from `edge-service` enrollment); `client_secret` stored
  in OS keychain (Windows Credential Manager / Linux Secret Service) or `~/.neuroassist/credentials`
  with mode 600
- On ACS unreachable: use cached token until expiry, then fail with `AUTH_UNAVAILABLE`

### 3.8 HeartbeatReporter

Sends `POST /api/v1/edge/heartbeat` to `edge-service` every 30s:

```json
{ "deviceId": "<id>", "status": "online", "fps": 14.8, "queueDepth": 0 }
```

Best-effort: EDGE unreachable does not stop the detection loop.

### 3.9 ConfigLoader

Loads configuration from (in priority order):
1. Environment variables (prefix `NEUROASSIST_`)
2. `~/.neuroassist/config.toml`
3. `./config.toml` (bundled defaults)

Required at startup: `backend.url`, `device.id`, `device.patient_id`, `device.tenant_id`.

---

## 4. Key quality attributes

| Attribute | Target | Mechanism |
|---|---|---|
| End-to-end latency | < 200ms (gesture start → event published) | 15fps capture, MediaPipe CPU, async publish |
| Offline resilience | 50 events buffered, vocabulary cached | SQLite outbox + local cache |
| Privacy (LGPD) | No video/frame ever leaves the device | Only `GestoDetectado` event (no video) |
| Hardware minimum | Host machine (Windows 10+/Ubuntu 22.04+) with integrated or USB webcam | MediaPipe CPU-only, ONNX < 5MB |
| Startup time | < 10s (warm), < 30s (cold, GEST sync) | Parallel init threads |
| Security | JWT RS256, device secret in OS keychain | ACS client_credentials |
| Observability | Structured JSON logs + EDGE heartbeat | Python `structlog`, 30s heartbeat |

---

## 5. LGPD compliance

The PEA processes biometric data (facial landmarks) locally on the patient's device.

**Critical constraint: no raw video or frames are ever transmitted to the backend.**
Only the classified event (`GestoDetectado`) — which contains a `Comando` ID and confidence
score — is sent over the network. Landmark coordinates are ephemeral (in-memory only, lifetime
of a single frame processing cycle).

---

## 6. Known gaps and constraints (MVP)

| Gap | Description | Mitigation |
|---|---|---|
| Model update mechanism | ONNX model is bundled; no auto-update | Manual PEA reinstall or `model_path` config override |
| Multi-patient per device | Not supported in MVP (1 device = 1 patient) | `device.patient_id` is static config |
| GPU acceleration | Not used (CPU-only MediaPipe) | Sufficient for 15fps on host machine; GPU path TBD post-MVP |
| Compound gesture reliability | Two-primitive matching depends on precise timing | `tempoMinGesto`/`tempoMaxGesto` are calibrated per-patient via PAC module |
| Offline alarm routing | Alarm is queued but caregiver is not notified while offline | Outbox + retry; alerting gap is a known MVP limitation |

---

## 7. Inter-system dependencies

| System | Interface | Direction | Fail mode |
|---|---|---|---|
| ACS (`acs-service`) | OAuth2 `client_credentials` grant | PEA → ACS | Fail: use cached JWT until expiry |
| GEST (`gest-service`) | `GET /vocabulary/{pacienteId}/active` | PEA → GEST | Degrade: use cached vocabulary |
| EVNT (`evnt-service`) | `POST /events` (`GestoDetectado`) | PEA → EVNT | Buffer in outbox, retry |
| EDGE (`edge-service`) | `POST /heartbeat`, `POST /enroll` | PEA → EDGE | Best-effort |
| EDGE (`edge-service`) | `GET /profile/{deviceId}` (detection profile) | PEA → EDGE | Use cached profile |

---

## 8. Repository structure (proposed)

```
neuroassist-edge-agent/
├── pyproject.toml
├── config.toml.example
├── Dockerfile
├── Makefile
├── src/
│   └── neuroassist_edge/
│       ├── __init__.py
│       ├── main.py               # entry point + signal handling
│       ├── config.py             # ConfigLoader
│       ├── capture.py            # CaptureModule
│       ├── landmarks.py          # LandmarkExtractor
│       ├── classifier.py         # GestureClassifier (ONNX)
│       ├── matcher.py            # CommandMatcher
│       ├── vocabulary.py         # VocabularyCache
│       ├── publisher.py          # EventPublisher + outbox
│       ├── auth.py               # AuthClient
│       ├── heartbeat.py          # HeartbeatReporter
│       └── models/               # ONNX model bundle
│           └── gesture_v1.onnx
├── tests/
│   ├── unit/
│   └── integration/
└── docs/
    └── (symlink or copy of architecture docs)
```

---

## 9. Out of scope (MVP)

- GUI / tray icon (headless process)
- Audio/vibration feedback on the device
- Custom gesture recording / model training
- Multi-tenant edge device (one device = one patient = one tenant)
- Native mobile (Android/iOS)
