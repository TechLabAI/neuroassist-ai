# PEA — Risks, Gaps and Assumptions

**Component:** Python Edge Agent | **Date:** 2026-07-29

---

## Risks

1. **Classifier false-positive on involuntary movements (safety-critical).** Patients with
   neurodegenerative diseases may have uncontrolled facial spasms that resemble intentional
   gestures. The `sensibilidade` + `tempoMinGestoMs` parameters are the primary mitigation —
   but they require careful per-patient calibration (PAC module). Risk: a false "emergência"
   alarm floods the caregiver. Residual risk accepted for MVP; feedback loop via EVNT
   (`false_positive` flag) provides data for calibration.

2. **Model drift: gestures change as disease progresses.** Facial muscle control in ALS/MND
   changes over time. A gesture classified as `piscar_direito` today may weaken or shift.
   The ONNX model is static in MVP (no auto-retrain). Mitigation: `pending-training →
   supported` lifecycle in GEST allows retiring degraded primitives and registering new ones.
   Risk: interim period where the model misclassifies.

3. **Camera unavailability.** USB webcam disconnect or driver failure stops detection.
   The agent logs CRITICAL and exits. On restart, detection resumes. The caregiver has no
   notification of this gap — a known MVP limitation.

4. **Backend services not yet started.** Since PEA and Java services share the machine, the
   PEA may start before the Java services finish booting (e.g. after a machine reboot).
   Mitigation: startup health-check loop (step 3 of startup sequence, 60s timeout).
   Risk: if services take > 60s, PEA exits and requires manual restart.

5. **Outbox overflow during prolonged service downtime.** If EVNT is down for an extended
   period (> 50 gesture matches), the oldest events are dropped. An emergency alarm during
   this window would be lost. Mitigation: observability via HeartbeatReporter (EDGE sees
   elevated `outboxDepth`). Risk accepted for MVP.

---

## Gaps Conhecidos

- **PAC command vocabulary endpoint.** `GET /pac/patients/{id}/commands/active` is not yet
  defined (PAC Layer 1 pending). The PEA startup step 6 (`CommandCache.fetch()`) will fail
  until PAC is architected and deployed. Tracked in: PEA-003, issue EDGE-PY-001.

- **ONNX model distribution.** The `gesture_v1.onnx` model is bundled with the agent binary.
  No mechanism exists to update it without reinstalling the PEA. A model versioning and
  distribution mechanism is post-MVP.

- **Multi-primitive compound timing calibration.** The 500ms window between `primitivoPrincipal`
  and `primitivoSecundario` is a hardcoded default. It should be per-patient configurable
  (PAC `Comando` field addition). Deferred to PAC Layer 1.

- **No caregiver notification of PEA downtime.** When the PEA exits (camera failure, crash),
  there is no alert to the caregiver via DASH. The EDGE heartbeat going stale is the only
  indirect signal. Direct notification is a post-MVP feature.

- **No GUI / system tray.** The PEA runs headless. On Windows, this means it must be
  launched from a terminal or configured as a service (NSSM). A minimal tray icon for
  status visibility is a UX gap.

---

## Suposições a Validar

1. MediaPipe FaceMesh achieves ≥ 90% landmark accuracy for patients with limited facial
   movement (ALS/MND). This assumption comes from the BA — not yet empirically validated
   with the target patient population.

2. The ONNX gesture classifier (< 5 MB, CPU-only) achieves the required confidence
   thresholds on the host machine at 15 fps. To validate: benchmark on target hardware before M5.

3. The co-located deployment (PEA + Java on same machine) has enough CPU/RAM for both
   workloads simultaneously. Minimum spec: 4-core ARM64 / x86-64, 4 GB RAM, 16 GB storage.
   Not validated yet.

4. A single patient per device is the correct MVP model (one `patient_id` in config).
   The design explicitly does not support a carer switching between patients on the same device.

5. The latency budget of < 200ms is achievable end-to-end on the minimum hardware with
   loopback HTTP. This should be measured in integration testing.

---

## Dependências Críticas

- **PAC (Catálogo de Comandos):** provides the patient command vocabulary endpoint. PEA
  cannot initialize CommandCache without it.
- **GEST (Detection Profile):** provides the versioned primitive catalog. PEA cannot
  start the classifier without it.
- **ACS:** provides OAuth2 `client_credentials` JWT. PEA cannot call any service without it.
- **EDGE:** provides enrollment (one-time setup) and heartbeat sink (30s reporting).
- **EVNT:** receives `GestoDetectado` events. Without EVNT, events queue in outbox.
