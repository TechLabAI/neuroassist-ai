# ADR-005 (CONV-local) — Agnostic dual input channel: gesture ⊕ touch → one selected-node model

- **Status:** proposed (2026-08-31)
- **Scope:** module-local (CONV frontend)
- **Decisor:** FE Architect (vision phase); ratifies the BA/UX intent of wireframe
  Tela 06 §5.3 (`InputChannelIndicator`) and ADR-014.

> Numbered ADR-005 per the generator convention for the module-critical decision. It
> is a **module-local** file and does not collide with the reserved shared ADR-005
> slot (different directory) — same convention GEST uses.

## Context

Tela 06 requires the patient to navigate the speech tree with "whatever movement they
can operate" — a recognized gesture from the in-browser GEST engine, **or** a direct
touch/mouse. The BA wireframe is explicit that the two channels **coexist without a
manual pre-selection** and reach the **same** target node ("mesmo destino alcançável
por gesto reconhecido OU por toque", §5.3/§5.5). The FE must decide how these two very
different input sources drive navigation without splitting the module into two code
paths that can drift.

## Decision

Model gesture and touch as **two thin adapters onto a single selected-node model**
owned by `SpeechTreeFacade`. Both adapters call the same facade method
(`selectNode(id)` / `activateNode(id)`); the facade holds exactly **one**
`selectedNodeId` signal. No navigation or playback logic anywhere in CONV branches on
*which* channel fired — the active channel is **presentational metadata only**
(`InputChannelIndicator`). The gesture adapter is fed by an injected input-channel
abstraction provided at the composition root (`scope:web`), so `libs/conv/*` never
imports `scope:gest` directly (respects `nx-boundaries.md`).

## Alternatives considered

1. **Two parallel navigation paths (gesture-driven vs touch-driven), each with its own
   state.** Rejected: guarantees drift — a fix to touch navigation would not reach
   gesture navigation; doubles the surface to test; contradicts the "same destination,
   no hierarchy between channels" UX intent.
2. **A manual channel selector (patient/caregiver picks gesture *or* touch up front).**
   Rejected: adds a decision the motor-limited patient should not have to make; the
   wireframe explicitly wants both live at once with a discreet indicator, not a switch.
3. **`scope:conv` imports the GEST engine directly.** Rejected: violates Nx boundaries
   (`scope:conv → scope:gest`) and couples CONV to GEST's internals; composition must
   happen at `scope:web` via an abstraction.

## Consequences

- **Positive:** one code path, one selected-node invariant, trivially testable channel
  parity (same node reachable both ways → identical state); either channel can fail
  independently while the other keeps working (aligns with the ADR-014 reliability
  risk — touch is always a full peer); clean Nx boundaries.
- **Negative:** requires a stable input-channel abstraction agreed with GEST (an open
  dependency, see doc 06 §2); the composition-root wiring is slightly more indirect
  than a direct import.

## References

- BA wireframe Tela 06 §5.3/§5.5 (`InputChannelIndicator`) — `techlab-ba/projects/
  neuro-assist-ai/design/wireframe.md`
- ADR-014 (GEST on the patient phone; Android/Chromium; dual identities)
- `docs/architecture/frontend/standards/nx-boundaries.md`
- CONV docs 02 §4, 03 §5 (channel-independence invariant), 04 §6
