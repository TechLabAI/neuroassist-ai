# SPEC-02 — Gesture Classification and Command Matching

**Component:** PEA | **Date:** 2026-07-30

---

## 1. GestureClassifier

- THE SYSTEM SHALL load the ONNX model from `capture.model_path` at startup and
  SHALL fail with `ModelLoadError` if the file is missing or corrupt.
- THE SYSTEM SHALL run ONNX inference in CPU-only mode (`onnxruntime.InferenceSession`
  with CPU execution provider).
- THE SYSTEM SHALL flatten the 468 landmarks into a 1404-float input tensor per frame.
- THE SYSTEM SHALL produce a `ClassificationResult(primitive_code: str, confidence: float)`
  for every `LandmarkSet` received.
- THE SYSTEM SHALL complete ONNX inference in < 10ms per frame on minimum hardware.

## 2. CommandMatcher — confidence thresholds

- THE SYSTEM SHALL apply the following confidence thresholds derived from `Comando.sensibilidade`:
  - `alta` → 0.90
  - `media` → 0.75
  - `baixa` → 0.60
- WHEN the classifier confidence for the dominant primitive is below the applicable threshold,
  THE SYSTEM SHALL treat the frame as "no gesture detected" for that Comando.

## 3. CommandMatcher — GestureWindow FSM

- THE SYSTEM SHALL maintain one independent `GestureWindow` state machine per `Comando`
  in the active `CommandVocabulary`.
- WHEN a `ClassificationResult` matches `Comando.primitivoPrincipal.engineRef` with
  confidence ≥ threshold, THE SYSTEM SHALL open a `GestureWindow` (start_time = now)
  or extend an existing one.
- WHEN the detected primitive changes or confidence drops below threshold,
  THE SYSTEM SHALL reset the `GestureWindow` to IDLE.
- WHEN a `GestureWindow` has been open for ≥ `Comando.tempoMinGestoMs` AND
  ≤ `Comando.tempoMaxGestoMs`:
  - IF the `Comando` has no `primitivoSecundario` → THE SYSTEM SHALL fire a `CommandMatch`.
  - IF the `Comando` has a `primitivoSecundario` → THE SYSTEM SHALL enter TRACKING_SECONDARY state.
- WHEN a `GestureWindow` duration exceeds `Comando.tempoMaxGestoMs`,
  THE SYSTEM SHALL reset to IDLE without firing a match.

## 4. CommandMatcher — compound commands

- WHEN in TRACKING_SECONDARY state, THE SYSTEM SHALL wait up to 500ms for the secondary
  primitive to be detected with confidence ≥ threshold.
- WHEN the secondary primitive is detected within 500ms, THE SYSTEM SHALL fire a `CommandMatch`.
- WHEN 500ms elapses without detecting the secondary primitive,
  THE SYSTEM SHALL reset to IDLE.

## 5. CommandMatcher — debounce (cooldown)

- AFTER a `CommandMatch` is fired for a given `comandoId`, THE SYSTEM SHALL enter a
  COOLDOWN state for that command lasting `Comando.tempoMaxGestoMs + 200ms`.
- WHEN in COOLDOWN for a `comandoId`, THE SYSTEM SHALL NOT fire another `CommandMatch`
  for that same `comandoId`.
- THE SYSTEM SHALL run cooldown timers per-command independently; other commands MAY
  match during another command's cooldown.

## 6. End-to-end latency

- THE SYSTEM SHALL achieve gesture-start to `CommandMatch` latency ≤ `Comando.tempoMinGestoMs +
  50ms` (the 50ms accounts for frame capture jitter and pipeline overhead).
- THE SYSTEM SHALL achieve `CommandMatch` to EVNT HTTP response latency ≤ 100ms on loopback.
- THE SYSTEM SHALL achieve total gesture-start to EVNT `202 Accepted` latency ≤ 200ms.
