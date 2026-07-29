# GEST — Event & Detection Contracts (`gest-service`)

> Module: **GEST — Detecção de Gesto (Edge)** · Service: `gest-service` · Package: `com.neuroassist.gest`
> **Messaging: disabled** (`stack.messaging.enabled = false`). There is **no broker** in the MVP:
> inter-service interaction is synchronous REST; the events below are **in-process domain events**.
> This document also freezes the **detection contract** — the cross-boundary schemas the edge uses to
> report a recognized command and to stream a live-test signal — which closes the open vision-phase
> warning. EARS notation (D-057) governs consume/publish behavior.

---

## 1. Messaging posture (MVP)

- THE SYSTEM SHALL NOT publish to or consume from any message broker in the MVP — there is none.
- The domain events in §2 are raised **in-process** (Spring `ApplicationEventPublisher`) and used for
  local side effects (audit append, cache invalidation, log emission). They are **not** a public bus
  contract.
- Cross-service coordination happens over REST (`api-contract.md`): PAC calls the eligibility guard,
  EDGE pulls the profile, EVNT receives edge reports shaped by §3.1.
- **Extraction path (doc 04 §"Broker deferred", Framing §11.2):** the events are named so that WHERE a
  broker is later introduced, `DetectionProfilePublished` and the recognized-command report are the
  first candidates to move onto an async topic with **no rename**. This is explicitly out of MVP scope.

---

## 2. In-process domain events

| Event | Raised when | In-process consumers | Future topic (deferred) |
|---|---|---|---|
| `PrimitiveRegistered` | a new `TipoGesto` enters the catalog as `registered` | audit log, DASH cache hint | `gest.primitive.registered.v1` |
| `PrimitivePromoted` | transition toward `supported` (`registered→pending_training→supported`) | audit append, DASH cache hint | `gest.primitive.promoted.v1` |
| `PrimitiveDeprecated` | a primitive leaves active use (`→ deprecated`) | audit append, eligibility cache invalidation | `gest.primitive.deprecated.v1` |
| `DetectionProfilePublished` | a new immutable profile version becomes `active` | prior-profile deactivation, profile cache warm | `gest.profile.published.v1` |

### 2.1 In-process event payloads (logical)

```json
// PrimitivePromoted
{ "tipoGestoId": "3f1c…", "fromState": "pending_training", "toState": "supported",
  "engineFeatureRef": "mp.blink.double.v1", "actor": "conductor:danilo", "at": "2026-07-27T13:00:00Z" }

// DetectionProfilePublished
{ "profileId": "b7…", "version": 7, "contractVersion": "1.0.0",
  "supportedPrimitiveIds": ["3f1c…"], "publishedAt": "2026-07-27T13:00:00Z" }
```

- WHEN a lifecycle transition commits, THE SYSTEM SHALL raise the corresponding `Primitive*` event
  after the DB transaction commits (post-commit), so no consumer observes an uncommitted state.
- WHEN a profile version becomes active, THE SYSTEM SHALL raise `DetectionProfilePublished` post-commit.
- IF the transaction rolls back, THEN THE SYSTEM SHALL NOT raise the event.

---

## 3. Detection contract (edge ↔ cloud) — FROZEN

The **detection contract** is GEST's public, versioned schema definition (ADR-005 BE / FE ADR-005). It
is NOT a broker message; it is a REST/stream payload shape that the edge, EVNT, and the FE live-test all
bind to. It is published as a descriptor at `GET /api/v1/gest/detection-contract` (api-contract §5.1).

**Contract version:** `1.0.0` (semantic). Carried in `X-Detection-Contract-Version` on every response
and in the payload field `contractVersion`.

### 3.1 `RecognizedCommandReport` — production telemetry (edge → EVNT)

The structured output the edge emits when the active command's primitive(s) are classified above
threshold. GEST owns its shape; **EVNT** is the receiver that builds the business `Evento`. GEST offers
a stateless validation endpoint (api-contract §5.2) but does not persist it.

```json
{
  "contractVersion": "1.0.0",
  "reportId": "6b0e…",              // client-generated UUID — idempotency key
  "deviceId": "D-12",
  "tenantRef": "empresa-0001",
  "profileVersion": 7,
  "commandRef": "cmd-abc",          // opaque handle from PAC Comando; GEST does not resolve it
  "recognizedAt": "2026-07-27T13:05:22.140Z",
  "primitives": [
    { "tipoGestoId": "3f1c…", "engineFeatureRef": "mp.blink.double.v1", "confidence": 0.91 }
  ],
  "timingWindow": { "startMs": 1200, "endMs": 1900, "durationMs": 700 },
  "composite": false
}
```

Field rules (EARS):

- THE SYSTEM SHALL treat `reportId` as the idempotency key (see §4).
- THE SYSTEM SHALL require `confidence` in the inclusive range `[0.0, 1.0]`.
- WHEN `composite = true`, THE SYSTEM SHALL require exactly two entries in `primitives`, and both
  `engineFeatureRef` values SHALL correspond to `supported` primitives in the referenced profile.
- IF `profileVersion` is not a version published for the tenant, THEN THE SYSTEM SHALL reject the
  report at the validation endpoint with `422 unknown-profile-version`.
- IF any reported `tipoGestoId` is not a member of the referenced profile version, THEN THE SYSTEM
  SHALL reject with `422 primitive-not-in-profile`.
- THE SYSTEM SHALL NOT accept or carry any video, image, or landmark data in this payload — only
  primitive identifiers, a scalar confidence, and timing metadata (privacy invariant, doc 02).

### 3.2 `EdgeRecognitionSignal` — live-test stream (edge agent → browser, localhost)

The lighter, non-persisted signal the local edge agent streams to the FE calibration/live-test panel
over a **localhost** channel (WS/SSE). Transport is FE↔edge (not served by gest-service); GEST freezes
only the **schema** so browser and agent agree (FE ADR-005, closes the vision warning).

```json
{
  "contractVersion": "1.0.0",
  "signalKind": "recognized | not_recognized | preview_frame_ack | degraded",
  "commandDraftRef": "draft-77",   // the in-progress Comando the caregiver is testing
  "primitives": [
    { "engineFeatureRef": "mp.blink.double.v1", "confidence": 0.88 }
  ],
  "timingWindow": { "startMs": 400, "endMs": 1100, "durationMs": 700 },
  "degraded": false,               // true ⇒ agent unavailable; verdict is mocked, never a fake "recognized"
  "emittedAt": "2026-07-27T13:06:00.010Z"
}
```

Behavior rules (EARS):

- WHILE the live-test session is open, THE SYSTEM SHALL stream `EdgeRecognitionSignal` messages tagged
  with the negotiated `contractVersion`.
- WHEN the edge bridge is unavailable (agent down / device offline / cross-device topology), THE SYSTEM
  SHALL emit `signalKind = "degraded"` with `degraded: true` and SHALL NOT emit `signalKind =
  "recognized"` — an honest degraded mode never reports a fake recognition (FE ADR-005).
- IF the browser and agent negotiate different `contractVersion` values, THEN THE SYSTEM SHALL treat
  the session as degraded until a compatible version is agreed.
- THE SYSTEM SHALL NOT persist any `EdgeRecognitionSignal`; it is ephemeral calibration feedback.

### 3.3 Bridge handshake / auth — jointly owned (open item to freeze in backlog)

- The localhost handshake (channel open, `commandDraft` framing) and the **device-credential auth** for
  the production report ingress depend on the **EDGE pairing** contract, still open (doc 06 "Gaps";
  api-contract §6.3). THE SYSTEM SHALL assume a JWT-shaped device token carrying `deviceId` + `tenant`
  until EDGE freezes the final shape (mTLS vs. OAuth2 `client_credentials`).
- This is the residual of the vision-phase warning: the **payload schemas (§3.1, §3.2) are now frozen**;
  the **transport auth** is deferred to a joint GEST/EDGE/edge-team decision recorded during backlog.

---

## 4. Idempotency & versioning

### 4.1 Idempotency

- THE SYSTEM SHALL use `reportId` (client-generated UUID) as the idempotency key for a
  `RecognizedCommandReport`.
- WHEN the report validation endpoint receives a duplicate `reportId` within a **24h** window, THE
  SYSTEM SHALL return the original validation verdict without side effects (validation is stateless, so
  duplicates are inherently safe; the window is a guidance for EVNT's persisting receiver).
- The eligibility guard (`api-contract §3`) and profile pull (`§4.4`) are naturally idempotent
  (read-only / conditional-GET), so THE SYSTEM SHALL treat repeated calls as equivalent.

### 4.2 Payload versioning

- THE SYSTEM SHALL version the detection contract semantically (`MAJOR.MINOR.PATCH`, starting `1.0.0`).
- WHEN a backward-compatible field is added, THE SYSTEM SHALL bump `MINOR` and keep old clients working.
- IF a breaking change is required, THEN THE SYSTEM SHALL bump `MAJOR` and expose both versions via the
  `contractVersion` negotiation at `GET /api/v1/gest/detection-contract` until edge clients migrate.
- THE SYSTEM SHALL bind each `detection_profile.contract_version` to the contract version it was
  published under, so an edge running an old profile knows exactly which contract to honor.

---

## 5. Summary

| Contract | Kind | Direction | Transport | Persisted |
|---|---|---|---|---|
| `PrimitiveRegistered/Promoted/Deprecated` | in-process domain event | internal | Spring app-events | audit only |
| `DetectionProfilePublished` | in-process domain event | internal | Spring app-events | profile row |
| `RecognizedCommandReport` | detection contract | edge → EVNT (validate at GEST) | REST | no (GEST) |
| `EdgeRecognitionSignal` | detection contract | edge agent → browser | localhost WS/SSE | no |
| Detection-contract descriptor | schema publication | GEST → edge/EVNT/FE | REST (`§5.1`) | n/a |
