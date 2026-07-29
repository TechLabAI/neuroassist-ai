# Python Edge Agent (PEA) — Brief

**Type:** Python deployable (external to Java monorepo) | **Repo:** `neuroassist-edge-agent/`
**Decision:** D-534 | **Layer 1:** 2026-07-29

The PEA is the local process running on the patient's device. It captures webcam frames, extracts
facial landmarks (MediaPipe), classifies them against a gesture model (ONNX), matches the result to
the patient's active command vocabulary (GEST detection profile + PAC commands), and publishes
`GestoDetectado` events to EVNT via HTTPS REST. No video or biometric data ever leaves the device.

**Consumed:** ACS (auth), GEST (detection profile), PAC (command vocabulary — pending PAC L1), EVNT (event sink), EDGE (enrollment + heartbeat).
**Published:** `GestoDetectado` → EVNT.
**Local persistence:** SQLite (`event_outbox`, `detection_profile_cache`, `command_cache`).
**Hardware (MVP):** Same server/PC where the Java services run, using the integrated or USB webcam. CPU-only, no GPU required. Future deployments may use dedicated hardware.
