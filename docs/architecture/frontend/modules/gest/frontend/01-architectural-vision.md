# 01 — Architectural Vision — GEST (Frontend)

## 1. Intent

At the end, a non-technical caregiver can, in a single screen, point Márcio's
webcam at himself (or himself+her), press **"Testar agora"**, and get an
unambiguous, jargon-free answer to one question: *"is the system seeing this
gesture?"* — before she trusts a newly configured or recalibrated command with
Márcio's safety.

GEST-FE is the **trust surface** of the detection engine: it does not detect,
it makes the edge detection *visible and provable* to a human.

## 2. Problem statement

**Current journey (without GEST-FE):** the caregiver configures a command
(label, gesture, sensitivity) blind — she has no way to know whether the edge
agent will actually recognize the movement until a real (or missed) alarm
happens. For a safety-critical channel this is unacceptable: a silent false
negative means Márcio cannot call for help.

**New journey:** configuration is *closed-loop*. Every command can be validated
live against the same engine that will run in production, and the result is
shown in plain language ("Gesto reconhecido com sucesso" / "Não reconhecido —
tente de novo ou aumente a sensibilidade"). Composite gestures — the higher-risk
case — get an explicit two-step test.

## 3. Personas (job-to-be-done)

- **Jamilly Amaral (Cuidadora Familiar) — primary.** JTBD: "When I add or tweak
  a command, prove to me it works *now*, so I can trust the alarm later." Uses
  the live-test inside `calibracao` repeatedly, in short interactions.
- **Danilo Amaral (technical conductor) — secondary, onboarding only.** JTBD:
  "During first setup, capture and validate the initial gesture once so the
  family starts from a working baseline." Uses the same surface inside the
  onboarding wizard's gesture step.

## 4. MVP scope (in)

- **Live-test panel** embedded in the command editor (`calibracao`) and the
  onboarding gesture step: webcam preview + start/stop + result feedback.
- **State machine** surfaced to the user: `idle → testing → recognized |
  not-recognized | camera-error` (recovery path on error).
- **Composite-gesture test**: sequence of two primitives with per-step feedback.
- **Human-language result**, mapped from the edge recognition signal (never raw
  confidence numbers on screen).
- **Camera lifecycle safety**: stream is always stopped on stop / navigate-away /
  destroy (no camera left running).

## 5. Out of scope (MVP)

- The detection model / landmark extraction / classification (Edge Agent, BE).
- Any video recording, upload, or persistence of frames.
- Standalone nav route / dashboard for GEST (it is an embedded feature, composed
  by other modules at the shell page level — see doc 02 §4 and ADR-006).
- Recalibration recommendations derived from feedback (post-MVP).

## 6. Architectural principles

- **Standalone-first** — every GEST component is `standalone: true`; the single
  `NgModule` is only the `gest.module.ts` lazy entry (ADR-006 / module-pattern).
- **Signals-first** — recognition/test state lives in a `signal<T>` inside the
  facade, exposed via `.asReadonly()`; no NgRx, no manual `BehaviorSubject`.
- **Lazy-by-default** — the module is loaded via `@neuroassist/gest`; the
  live-test feature is `loadComponent`-lazy where composed.
- **OnPush mandatory** on every component.
- **Detection stays on the edge** — the browser never owns the classifier; it
  renders and streams a result (avoids engine drift, ADR-005).
- **Boundary discipline** — `scope:gest` imports only `scope:gest` +
  `scope:shared`; cross-module composition happens only at `scope:web`
  (nx-boundaries.md).
- **Privacy-by-default** — camera on only during an explicit test; no frame ever
  persisted or transmitted off-device.

## 7. Success signals

- **Time-to-first-feedback** of a live test ≤ 1.5 s after "Testar agora"
  (camera up + first recognition signal), degrading gracefully.
- **Camera-error clarity**: 100% of permission/hardware failures produce the
  recovery message, never a silent dead preview.
- **Recognition parity**: the live-test verdict matches what the production edge
  agent would decide for the same command (same engine, ADR-005) — no
  "passed the test but misses in production".
- **p95 recognition-signal latency** from edge agent ≤ 300 ms while testing.
- **Zero camera leaks**: no `MediaStream` track remains active after test
  stop / route change (asserted in e2e).
