# SPEC-05 — Detection Profile and Command Vocabulary

**Component:** PEA | **Date:** 2026-07-30

---

## 1. Detection profile loading (GEST)

- WHEN the PEA starts (after token obtained), THE SYSTEM SHALL call
  `GET /gest/devices/{deviceId}/detection-profile` and cache the result in
  `detection_profile_cache`.
- THE SYSTEM SHALL fall back to the cached detection profile in SQLite if GEST
  is unreachable at startup (HTTP error or timeout).
- WHEN no cached profile exists and GEST is unreachable, THE SYSTEM SHALL log
  CRITICAL and exit with code 1.
- WHEN GEST returns `404 Not Found`, THE SYSTEM SHALL log CRITICAL
  `device not enrolled in GEST` and exit with code 1.

## 2. Detection contract loading (GEST)

- WHEN the detection profile is loaded, THE SYSTEM SHALL call
  `GET /gest/detection-contract` and store the ONNX model path or URL from the
  response.
- WHEN the contract specifies a `modelUrl`, THE SYSTEM SHALL download the ONNX
  file to `{app.data_dir}/models/{modelVersion}.onnx` if not already present.
- THE SYSTEM SHALL verify the SHA-256 of the downloaded model against
  `detection-contract.modelChecksum` before loading; log CRITICAL and exit if
  mismatch.

## 3. Command vocabulary loading (PAC)

- WHEN the PEA starts (after detection profile loaded), THE SYSTEM SHALL call
  `GET /pac/patients/{patientId}/commands/active` and cache the result in
  `command_cache`.
  (`patientId` is resolved from `detection_profile_cache.patientId`.)
- THE SYSTEM SHALL fall back to the cached command vocabulary in SQLite if PAC
  is unreachable at startup.
- WHEN no cached vocabulary exists and PAC is unreachable, THE SYSTEM SHALL log
  CRITICAL and exit with code 1.
- THE SYSTEM SHALL build the in-memory `CommandVocabulary` from the PAC response,
  grouping Comandos by `primitivoPrincipal.engineRef`.

## 4. Cache refresh

- THE SYSTEM SHALL refresh `detection_profile_cache` and `command_cache` every
  `{cache.refresh_interval_minutes}` minutes (default 15).
- WHEN a refresh call fails (network error, 5xx), THE SYSTEM SHALL retain the
  existing in-memory vocabulary and log WARN; no restart required.
- WHEN a refresh returns a changed `modelVersion` in the detection contract,
  THE SYSTEM SHALL download the new ONNX model and hot-swap the
  `GestureClassifier` without restarting the process.
- WHEN `CommandCache.invalidate()` is called (triggered by EVNT 422 `unknown-command`),
  THE SYSTEM SHALL immediately trigger a PAC refresh cycle out of schedule.

## 5. Model hot-swap

- THE SYSTEM SHALL load the new ONNX model in parallel (do not stop inference on
  the current model).
- WHEN the new model is loaded and verified, THE SYSTEM SHALL atomically replace
  the `GestureClassifier` in the pipeline (swap reference under asyncio lock).
- WHEN the hot-swap succeeds, THE SYSTEM SHALL log INFO `model hot-swapped to {version}`.
- WHEN the hot-swap fails (download error, checksum mismatch, load error),
  THE SYSTEM SHALL retain the current model and log ERROR.
