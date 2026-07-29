<!-- OUTPUT-SCHEMA
- Intent
- Problem Statement
- Personas
- MVP Scope
- Out of Scope
- Architectural Principles
- Success Signals
-->

# 01 — Architectural Vision

## Intent
EDGE is the backend **device authority** of NeuroAssist AI. It does not run computer vision and does
not author the detection profile. Its purpose is to own the identity of each edge agent, bind it to a
single patient, and turn a stream of heartbeats into a trustworthy answer to one question: *is this
patient actually being watched right now?* Everything visual stays on the edge; GEST owns *what is
detectable*; EDGE owns *whether the device that detects it is registered, paired and alive*.

## Problem Statement
The gesture detection engine runs on a local agent on the patient's device. For a safety product that
turns residual movements into help requests, three facts must be indisputable at the backend: (1)
which physical device is watching which patient, (2) that the device authenticated as itself and no
one impersonated it, and (3) that it is alive *now* — a camera that silently went offline is the most
dangerous failure mode, because the family believes Márcio can still call for help when he cannot.
No other module owns this. GEST hands the device a detection profile but does not know if the device
exists; PAC records `paciente.dispositivo_edge_id` but delegates its validity to EDGE; DASH must show
"online/offline" but has no primary signal of its own. EDGE is that single authority.

## Problem Statement — non-goals
EDGE does not capture or process any pixel of video, does not decide whether an event is an emergency
(EVNT), does not author or version the detection profile (GEST — EDGE only *delivers* the current
version pointer and reports drift), and does not model patients or commands (PAC). It is the device
identity, pairing and liveness layer between the edge agent and the rest of the backend.

## Personas
- **Primary — Danilo Amaral (technical conductor):** during onboarding, registers the edge agent and
  pairs it to the patient's device (one active device per patient), and re-pairs when a device is
  replaced.
- **Secondary — Jamilly Amaral (family caregiver):** never touches EDGE directly, but through DASH she
  depends on EDGE to answer "is the camera watching Márcio online?" and to warn her the moment the
  device drops. A stale heartbeat is a caregiver-facing safety signal, not an operational detail.

## MVP Scope
1. **Device registration** — an edge agent obtains a backend identity (`DispositivoEdge`) and the
   credentials it authenticates with on every subsequent call.
2. **Pairing** — bind a registered device to exactly one `Paciente` via an explicit, operator-driven
   claim; unpair on replacement. Cross-service reference validated for PAC via a port, never a DB FK.
3. **Heartbeat & liveness** — periodic pull-based heartbeat; EDGE derives `online`/`offline` from
   heartbeat freshness (staleness threshold), records `ultimaSincronizacao` and agent `versao`, and
   exposes liveness to DASH/PAC.
4. **Detection-profile delivery** — on heartbeat, hand the paired device the current profile *version
   pointer* owned by GEST and record the version the device is actually running (drift detection).

## Out of Scope
- Computer-vision execution (webcam, landmarks, classification) — edge deployable, not Java.
- Authoring the detection profile, primitive catalog, or detectability lifecycle — GEST.
- Patient/command data (PAC), caregiver identity/roles (ACS), event/alarm/notification (EVNT),
  immutable audit trail (HIST).
- Over-the-air update of the edge agent binary (only version *reporting*, not pushing a new build).
- Any transport of video, images or landmarks off the edge device.

## Architectural Principles
1. **Silence is a failure, not a default** — a missing heartbeat becomes an explicit `offline`
   signal within a bounded staleness window; EDGE never lets a dead device look neutral.
2. **A device is only trusted when it proves it is itself** — every heartbeat and pull is
   authenticated as the registered device; identity is never inferred from a payload field.
3. **One patient, one active device (MVP)** — pairing is exclusive; re-pairing is explicit and audited.
4. **EDGE transports, GEST authors** — EDGE delivers the profile version pointer and detects drift; it
   never mutates or authors profile content.
5. **Decoupled and deterministic** — no LLM, no TechLab AI dependency (`ai.hasAi = false`); liveness is
   a deterministic function of heartbeat freshness, computed the same way for every reader.

## Success Signals
- Every `paciente.dispositivo_edge_id` in PAC resolves to a real, registered, currently-paired device.
- A device that stops sending heartbeats is reflected as `offline` to the caregiver within the agreed
  staleness window — with zero false "online" once the window has elapsed.
- An edge agent can bootstrap from a cold start: register → pair → first heartbeat → receive current
  profile version pointer, with no manual DB intervention.
- DASH can always answer "is monitoring live?" from a single EDGE-owned signal, and profile-version
  drift between backend and device is detectable, not silent.
