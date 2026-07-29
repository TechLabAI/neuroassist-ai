# PEA — Initial Domain Framing

**Component:** Python Edge Agent | **Date:** 2026-07-29

---

## 1. Domain context

The PEA sits at the boundary between the physical world (patient's residual voluntary movements)
and the NeuroAssist AI signal chain. Its domain is **real-time gesture recognition and command
dispatch** — translating continuous video frames into discrete, structured help requests.

The patient cannot use a keyboard, mouse, or voice. A small facial movement (a blink, an eye
twitch) is the only available input channel. The domain must therefore handle:
- **Ambiguity** — is this a deliberate gesture or an involuntary movement?
- **Timing** — how long must a gesture be held to count?
- **Compound sequences** — two primitives in sequence form one command
- **False-positive cost** — an unintended "emergência" alarm disrupts the caregiver's workflow

---

## 2. Ubiquitous language

| Term | Definition |
|---|---|
| **Frame** | A single BGR image captured from the webcam (not stored, ephemeral) |
| **LandmarkSet** | The 468 normalized 2D/3D facial landmark coordinates extracted from a frame by MediaPipe. Lifetime: one frame cycle. Never persisted. |
| **Primitive gesture** (TipoGesto) | A single, atomic facial movement recognizable by the ONNX classifier (e.g. "piscar_direito", "piscar_duplo"). Defined in GEST. |
| **Detection profile** | The versioned set of primitives the GEST service has designated as `supported` for the patient's tenant. The ONNX model is calibrated to this profile. |
| **Comando** | A patient-specific mapping from one or two primitives (with timing and sensitivity) to a labeled action ("Água", "Emergência"). Owned by PAC. |
| **ClassificationResult** | Output of the ONNX model for a single frame: `{primitive_code, confidence}`. |
| **GestureWindow** | In-memory state tracking how long a specific primitive has been continuously detected. Resets if confidence drops below threshold or a different primitive dominates. |
| **CommandMatch** | The event fired when a `GestureWindow` satisfies a `Comando`'s timing and sensitivity constraints. Contains `comandoId`, `urgency`, `durationMs`, `confidence`. |
| **GestoDetectado** | The structured event published to EVNT. The only data that leaves the PEA (no video, no landmarks). |
| **Outbox** | SQLite table holding undelivered `GestoDetectado` events. Survives process restarts. |
| **Detection profile cache** | Local SQLite snapshot of the GEST detection profile, keyed by ETag/version. |
| **Command cache** | Local SQLite snapshot of the patient's active `Comando` list from PAC. |
| **Confidence threshold** | The minimum ONNX classifier score required for a primitive to be considered detected. Derived from `Comando.sensibilidade`: `alta=0.90`, `media=0.75`, `baixa=0.60`. |
| **Cooldown** | After a `CommandMatch`, the period during which the same command cannot be matched again. Duration: `Comando.tempoMaxGestoMs + 200ms`. |
| **Compound command** | A `Comando` with both `primitivoPrincipal` and `primitivoSecundario`. The second primitive must follow within 500ms after the first window closes. |
| **Enrollment** | One-time setup where the device receives its `clientId`/`clientSecret` from EDGE via a one-time token. |

---

## 3. Domain invariants

| # | Invariant | Enforcement |
|---|---|---|
| I-01 | No video, image, or landmark coordinate is ever persisted to disk or transmitted over the network. Only `CommandMatch` metadata leaves the in-memory pipeline. | `structlog` field blocklist; no file writes in capture/landmark/classifier path |
| I-02 | A `CommandMatch` is only fired if the gesture window duration is within `[tempoMinGestoMs, tempoMaxGestoMs]`. | `CommandMatcher` FSM |
| I-03 | After a `CommandMatch`, the same `comandoId` cannot match again until the cooldown expires. | `CommandMatcher` per-command cooldown map |
| I-04 | The outbox never exceeds 50 entries. On overflow, the oldest entry is dropped (ALERT logged). | `EventPublisher.enqueue()` |
| I-05 | The PEA never starts the detection loop without a valid detection profile from GEST. | Startup step 5 (fail-fast) |
| I-06 | A `GestoDetectado` event is always idempotent: same `eventId` reused on retry. | `event_outbox.event_id` PRIMARY KEY; `X-Idempotency-Key` header |

---

## 4. Domain model (PEA-internal)

```
DetectionProfile
  ├── version: str
  └── primitives[]: { tipoGestoId, engineFeatureRef, nome }

CommandVocabulary
  └── comandos[]: Comando
        ├── comandoId: uuid
        ├── primitivoPrincipal: { tipoGestoId, engineRef }
        ├── primitivoSecundario?: { tipoGestoId, engineRef }
        ├── sensibilidade: alta | media | baixa
        ├── urgencia: emergencia | rotina
        ├── tempoMinGestoMs: int
        └── tempoMaxGestoMs: int

GestureWindow (in-memory, per primitive_code)
  ├── primitive_code: str
  ├── start_time: float (epoch ms)
  ├── last_seen: float
  ├── duration_ms: int (accumulated)
  └── confidence_history: deque[float] (last 5 frames)

CommandMatch (in-memory event)
  ├── comandoId: uuid
  ├── urgency: emergencia | rotina
  ├── durationMs: int
  ├── confidence: float
  └── capturedAt: datetime (UTC)

OutboxEntry (SQLite)
  ├── event_id: uuid (PK)
  ├── comando_id: uuid
  ├── payload_json: str (full GestoDetectado JSON)
  ├── enqueued_at: str (ISO-8601)
  ├── attempt_count: int
  ├── next_retry_at: str
  └── last_error?: str
```

---

## 5. Bounded context relationships

```
PEA (internal domain)
  ├── consumes: GEST.DetectionProfile (classification config)
  ├── consumes: PAC.CommandVocabulary (timing/urgency mapping)
  ├── consumes: ACS.JWT (identity — role edge_agent)
  ├── consumes: EDGE.DeviceProfile (capture config — fps, resolution)
  ├── produces: EVNT.GestoDetectado (one structured event per CommandMatch)
  └── reports: EDGE.Heartbeat (liveness + fps + outbox depth)

GEST owns: what can be detected (TipoGesto catalog + detectability lifecycle)
PAC owns: what a detection means for THIS patient (Comando mapping)
PEA owns: the real-time detection loop and dispatch — no business rules beyond timing
EVNT owns: what to do with a GestoDetectado (alarm routing, notification)
```

The PEA has **no business rules about alarms** — it fires `GestoDetectado` and EVNT decides
urgency handling. The PEA knows urgency only to include it in the event payload (from PAC);
it does not implement notification logic.
