<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
`edge-service` is an independent Spring Boot microservice (ADR-002 shared) that owns the
**device-integration bounded context**: the `DispositivoEdge` aggregate, its pairing to a patient, and
its heartbeat-derived liveness. Inside the boundary: device registration, credential issuance,
pairing/unpairing, heartbeat ingestion, liveness computation, and delivery of the current
detection-profile *version pointer* (a value fetched from GEST, not authored here). Outside the
boundary: the CV engine and any pixel processing (edge deployable), profile authoring and the
primitive catalog (GEST), patient/command data (PAC), caregiver identity (ACS), events/alarms (EVNT),
audit trail (HIST). The service holds no biometric or video data.

## External Actors and Systems
- **Edge agent** (on the patient's device) — the only caller of registration, pairing-claim and
  heartbeat endpoints; authenticates as itself on every call. Reaches the service through GWY.
- **Technical conductor (Danilo)** — via DASH/admin UI, triggers device registration approval and the
  pairing of a device to a patient.
- **GEST (gest-service)** — EDGE calls GEST (sync REST, discovered via REG) to obtain the active
  detection-profile version pointer for the paired patient.
- **PAC (pac-service)** — calls EDGE (`EdgeDevicePort`) to validate `paciente.dispositivo_edge_id` at
  write time.
- **DASH (dash-service)** — reads EDGE liveness (`online`/`offline`, `ultimaSincronizacao`, `versao`)
  to render the caregiver's monitoring status and stale-device warning.
- **Platform infra** — GWY (routing), REG/Eureka (discovery), MIG (centralized Flyway), the OAuth2
  authorization boundary shared across services.

## End-to-End Flows

**Golden path — device onboarding and steady-state monitoring**
1. Operator provisions the edge agent; the agent calls `register` and receives a `DispositivoEdge`
   identity plus device credentials (ADR-005).
2. Operator pairs the device to the patient (exclusive: one active device per patient); EDGE records
   the pairing and PAC can now resolve `dispositivo_edge_id`.
3. The device sends its first authenticated **heartbeat**; EDGE marks it `online`, stores
   `ultimaSincronizacao` and `versao`, and returns the current detection-profile version pointer
   (fetched from GEST) plus the version it should be running (ADR-006).
4. The device keeps heartbeating on its interval; each heartbeat refreshes liveness and reports the
   profile version actually running (drift check).

**Alternative — heartbeat goes stale (safety-critical)**
- No heartbeat arrives within the staleness window → a liveness read derives `offline` deterministically
  (ADR-006). DASH surfaces the drop to the caregiver. There is **no** event bus in the MVP
  (`messaging.enabled = false`), so DASH learns of the drop by **polling** EDGE liveness, not by a push
  — an explicit trade-off recorded in doc 06.

**Alternative — device replacement**
- Operator unpairs the old device and pairs a new one; the old identity is retired (audited), and PAC's
  `dispositivo_edge_id` is updated through its own write path against the new device.

**Alternative — profile drift**
- The version the device reports running differs from the current GEST pointer → EDGE flags drift on the
  heartbeat response so the device re-pulls; drift is visible to DASH, never silent.

## Internal Boundaries
Hexagonal (ADR-001 shared), four layers:
- **api** — REST controllers for the edge agent (register, pair-claim, heartbeat) and for internal
  consumers (liveness query for DASH, device-validation for PAC); DTOs; auth entrypoint.
- **application** — use cases: `RegisterDevice`, `PairDeviceToPatient`, `UnpairDevice`,
  `RecordHeartbeat`, `EvaluateLiveness`, `ResolveDeviceForPatient`, `DeliverProfilePointer`.
- **domain** — the `DispositivoEdge` aggregate, the pairing invariant, the liveness policy (staleness
  threshold → `online`/`offline`), and the pairing/lifecycle state machine.
- **infrastructure** — JPA persistence (per-empresa schema), the outbound port to GEST
  (`DetectionProfilePort`), device-credential verification, and the inbound adapter PAC calls.

## Cross-cutting Concerns
- **Security** — every device call is authenticated *as that device* (ADR-005); a heartbeat can only
  refresh the identity it authenticated as, never an id passed in the body. Operator actions
  (register-approve, pair) require an authenticated conductor role (ACS/OAuth2 via GWY).
- **Observability** — heartbeat ingestion, liveness transitions and profile-drift flags are the key
  signals; liveness transitions (`online↔offline`) are logged with correlation ids for the safety
  audit. Health/readiness per the platform standard.
- **Multi-tenancy** — schema-per-empresa (ADR-003 shared). A device belongs to exactly one empresa;
  every read/write is tenant-routed. `tenant_id` is mirrored on the row for defense-in-depth, matching
  PAC's convention.
- **Time & liveness** — liveness is a pure function of `now − ultimaSincronizacao` vs the staleness
  threshold, evaluated at read time; no background job is required to "flip" a device offline (avoids a
  scheduler dependency in the MVP), though a periodic sweep may be added later for proactive alerting.
- **LGPD** — EDGE stores device metadata and a patient reference (`pacienteId`), not biometric or video
  data. The `pacienteId` link is personal data by association and lives in the per-empresa schema; no
  media is ever persisted (privacy invariant inherited from GEST doc 02).
