# ADR-006 — EAR/BRI Interim Detector for Live-Test Bridge (pre-PEA period)

> **SUPERSEDED (2026-08-22)** por `GEST ADR-007 — O browser é o único motor de detecção`.
> Sem objeto: ela ratificava o detector interino da ponte Python, que deixa de existir.

- **Status:** accepted (2026-08-12) — amends ADR-005 to ratify interim detector algorithm
- **Scope:** local (module GEST) + edge-agent runtime

## Context

`ADR-005` (Live gesture test bridges to the local Edge Agent) was accepted, establishing
that the live-test panel bridges to the real edge agent — not browser ML — to prevent
engine drift (a command calibrated via one algorithm passing but failing production detection
under a different one).

During PoC-to-real implementation (plan-GEST-poc-to-real-architecture.md §4), two distinct
artefacts were built for the live-test path:

| Artefact | Detection algorithm | Status |
|---|---|---|
| `backend/edge-agent-ws.py` (**new**) | EAR (Eye Aspect Ratio) + BRI (Brow Raise Index) threshold/hysteresis | implemented, smoke-tested |
| `docs/architecture/edge-agent/python-edge-agent/` (PEA formal design) | ONNX classifier (`gesture_v1.onnx`) | documented, not yet implemented |
| `calibracao.component.ts` `PocGestureDetector` | Browser-side MediaPipe FaceLandmarker (EAR-based) | prototype-only, 368 lines in design-reference |

The gap: `ADR-005`'s engine-drift rationale assumes the live-test bridge and the production
detector share the same algorithm. Currently they would not: `edge-agent-ws.py` uses EAR/BRI;
the PEA uses ONNX. If both ran, a gesture could calibrate via EAR/BRI thresholds and fail
production ONNX — precisely the failure mode ADR-005 was written to prevent.

There is no ONNX classifier implementation available yet to resolve this through algorithm
alignment.

## Decision

**Accept EAR/BRI as a documented, time-boxed interim detector inside `edge-agent-ws.py`.**
This is Option 1 from `plan-GEST-poc-to-real-architecture.md §4`.

Rationale:
1. EAR/BRI and the planned ONNX classifier share the same gesture *vocabulary*
   (primitives in the TipoGesto catalog). Algorithm mismatch is a calibration-threshold
   concern, not a coverage concern — a gesture that never fires in production would be
   visible during a real-world smoke test before any patient is onboarded.
2. The degraded-mode invariant from ADR-005 is preserved: `edge-agent-ws.py` never emits
   a fake "recognized" — only genuine detections from the EAR/BRI engine, or explicit
   degraded signals.
3. Keeping `PocGestureDetector` (browser-side MediaPipe from the prototype) out of the
   production path is more important than algorithm purity during this interim period. The
   prototype detector is **prototype-only** — it is never imported or used outside of
   `frontend/design-reference/angular-prototype/`.
4. The interim is explicitly bounded: `edge-agent-ws.py`'s detector must be **replaced
   by or unified with the PEA's ONNX engine** before the product leaves closed pilot
   (tracked as `INT-GEST-004` / `INT-GEST-007`).

**`PocGestureDetector` disposition:** confirmed as prototype-only. It must never be
promoted into `libs/gest/` or any production-path code. The `EdgeDetectionPort` /
`EdgeDetectionHttpAdapter` (WS client) path from this session is the production live-test
mechanism.

## Interim safeguards (non-negotiable)

1. `edge-agent-ws.py` must include a visible `# INTERIM: EAR/BRI detector — not ONNX`
   comment block at the top of its detection loop, referencing this ADR.
2. A caregiver-facing banner in the live-test panel must state: "Calibrating with gesture
   detection (preview mode — accuracy will improve with ONNX classifier)" when
   `edgeLiveBridge` is active and `agentVersion` does not include an ONNX classifier.
   (Implementation: the WS handshake response includes `detectorType: "ear_bri" | "onnx"`;
   the FE renders the banner when value is `"ear_bri"`.)
3. No `urgencia = emergencia` command may be shipped to a real patient while this interim
   is in effect without an explicit, signed-off calibration session with the `"condutor técnico"`.
4. This ADR is reviewed and either confirmed or superseded when the PEA ONNX implementation
   milestone is reached.

## Consequences

**Positive:** live-test bridge can be used for E2E testing, demos, and closed-pilot onboarding
today; `PocGestureDetector` ambiguity is closed; no browser ML in the production path.

**Negative / trade-off:** the engine-drift risk from ADR-005 is accepted as a bounded, visible
interim — not silently. If EAR/BRI thresholds and ONNX decision boundaries diverge significantly
for any supported primitive, calibrated commands may behave differently in production until PEA
is unified with the live-test bridge.

## References

- `ADR-005-live-test-via-edge-agent-stream.md` (this ADR amends its interim status)
- `plan-GEST-poc-to-real-architecture.md §4` (discovery document, architecture review context)
- `plan-frontend-poc-to-real-architecture.md §2.2, §4.6` (PocGestureDetector disposition)
- `docs/architecture/edge-agent/python-edge-agent/` ADR-001..005 (PEA formal design)
- `INT-GEST-004` / `INT-GEST-007` (auth/bridge unification backlog items)
