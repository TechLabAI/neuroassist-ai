# ADR-005 — Live gesture test bridges to the local Edge Agent (no browser ML)

- **Status:** accepted (2026-08-12) — implemented via `edge-agent-ws.py`; interim detector ratified by ADR-006
- **Scope:** local (module GEST)

## Context

GEST's frontend surface is the **live gesture test**: before a caregiver trusts a
newly configured or recalibrated `Comando`, she runs it live and gets a verdict
("recognized" / "not recognized"). The actual detection — webcam frames →
facial-landmark extraction → multi-class classification of the active command —
runs on the **local Edge Agent** on the patient's device (project stack:
edge→backend sync over REST/outbox; no browser detection).

The prototype (`calibracao.component.ts`) turns the webcam on with `getUserMedia`
and then **simulates** the verdict via a timeout — there is no real recognition.
We must decide where the *real* live-test verdict comes from in production, for a
**safety-critical** channel where a false negative means Márcio cannot ask for help.

Options:

1. **Browser-native ML** — run a client-side model (MediaPipe / TF.js) in the
   panel purely for the calibration preview.
2. **Bridge to the local Edge Agent** — the panel opens a localhost channel
   (WebSocket/SSE) to the same agent that detects in production, sends the command
   draft as context, and renders the agent's recognition stream.
3. **Keep the simulation** — verdict is mocked (prototype behavior).

## Decision

**The live test bridges to the local Edge Agent's recognition stream (option 2).**
The browser owns only the local webcam *preview* (`getUserMedia`, for the
operator's visual confirmation) and *renders* the recognition signals emitted by
the edge engine over a localhost channel. `libs/gest/data-access` exposes an
`EdgeDetectionClient.openStream(commandDraft)` returning an
`Observable<EdgeRecognitionSignal>`, bridged into a signal by `GestureTestFacade`.

When the edge bridge is unavailable (agent down, device offline, or panel on a
different device), the surface enters an **explicit degraded mode**: preview still
works, the verdict is mocked, and a banner states that no real recognition
occurred. It **never** reports a fake "recognized". A feature flag
`edgeLiveBridge` (default on) governs this.

## Alternatives considered

1. **Browser-native ML (option 1)** — rejected: creates **engine drift**. A model
   in the browser would decide differently from the production edge classifier, so
   a command could pass calibration and still miss in production — unacceptable for
   a safety channel. Also adds significant bundle weight to a panel that stays open
   in the background.
2. **Keep the simulation (option 3)** — rejected for production: gives false
   confidence. Retained only as the degraded/offline fallback, clearly labeled.

## Consequences

- **Positive:** what you test is what detects — no drift; the browser carries no ML
  weight; privacy preserved (frames never leave the device, nothing persisted);
  degraded mode is honest and explicit.
- **Negative:** introduces a **localhost bridge contract** with the edge agent
  (protocol, message schema, handshake/auth) that must be frozen in specs with the
  BE/edge team. Assumes panel and agent are commonly co-located (same device); the
  different-device topology falls back to degraded mode (see Framing §6 A1/A2).
- **Follow-up:** composite-gesture tests (two primitives) are higher-risk for false
  negatives; the two-step live test is the primary mitigation surface.

## References

- `docs/architecture/frontend/modules/gest/frontend/02-architecture-blueprint.md` (§3 flows)
- `docs/architecture/frontend/modules/gest/frontend/05-state-and-data-flow.md` (§2 data access)
- `docs/architecture/frontend/modules/gest/frontend/06-risks-gaps-assumptions.md` (R1, R2)
- BE Framing §6.1/§7.1a/§9.3 (engine capability vs catalog naming)
- LBC §12 Amendment 1 (composite-gesture false-negative risk)
