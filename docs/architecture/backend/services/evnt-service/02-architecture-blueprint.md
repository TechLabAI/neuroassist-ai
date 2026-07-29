<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
`evnt-service` is an independent Spring Boot microservice (ADR-002 shared) whose boundary contains:
- **Inbound:** the report ingress endpoint the edge posts to, and the caregiver confirmation endpoint
  DASH calls.
- **Owned state:** the `Evento` aggregate and its notification-dispatch records, persisted in the
  tenant schema (`t_<empresa>`), plus a small idempotency ledger keyed by `reportId`.
- **Outbound (synchronous REST):** validation calls to GEST, resolution calls to PAC (`Comando`) and
  ACS (caregiver/routing), notification delivery to DASH, and audit append to HIST.

Outside the boundary: computer vision and primitive vocabulary (edge/GEST), command authoring (PAC),
device pairing (EDGE), audit storage (HIST), and panel rendering (DASH). EVNT never sees a video frame.

## External Actors and Systems
| Actor / System | Role toward EVNT | Interaction |
|---|---|---|
| **Edge agent** | Produces recognized-command reports | POST `RecognizedCommandReport` (REST/HTTPS via GWY, device token) |
| **GEST** (`gest-service`) | Validation authority for the report | Synchronous REST — validate report against profile/primitives |
| **PAC** (`pac-service`) | Owns `Comando` + `Paciente` | Synchronous REST — resolve command urgency/sensitivity + patient |
| **ACS** (`acs-service`) | Owns caregivers + routing | Synchronous REST — resolve responsible caregiver for the patient |
| **DASH** (`dash-service`) | Renders alarm / caregiver surface | EVNT dispatches notification; DASH calls back the confirmation endpoint |
| **HIST** (`hist-service`) | Immutable audit trail | Synchronous REST — append audit facts (created/confirmed/resolved) |
| **GWY / REG** | Gateway + service discovery | Routing and discovery of the above (independent processes) |

## End-to-End Flows

### Golden path — emergency help request
1. Edge classifies the active command and POSTs a `RecognizedCommandReport` (with `reportId`) to EVNT.
2. EVNT checks the **idempotency ledger**; if `reportId` is new, it proceeds (else returns the prior verdict).
3. EVNT calls **GEST** to validate the report against the referenced profile/primitives.
4. EVNT calls **PAC** to resolve the `Comando` → label, `urgencia = emergencia`, `sensibilidade`, patient.
5. EVNT **persists the `Evento`** (`feedback = nao_avaliado`), snapshotting command label + urgency + timing.
6. EVNT resolves the responsible caregiver via **ACS** and dispatches a **full-screen alarm** notification
   to **DASH**; the dispatch record tracks delivery status (queued → delivered → acknowledged).
7. EVNT appends an audit fact to **HIST** (`EventoCriado`).
8. The caregiver acts and confirms via DASH → EVNT transitions the Evento to `real` (or `falso_positivo`),
   records `confirmadoPor`, and appends `EventoConfirmado` to HIST.

### Alternative flows
- **Routine request:** identical, but step 6 dispatches a **discreet** notification tier (no full-screen alarm).
- **Duplicate/retried report:** step 2 short-circuits — the original Evento and verdict are returned; no new event.
- **GEST rejects the report** (unknown profile version / primitive not in profile): EVNT does **not** create an
  Evento; it returns `422` to the edge and records the rejection for observability (never a silent drop).
- **Notification delivery fails** (DASH down / caregiver offline): the Evento is still persisted; the dispatch
  record stays `queued`/`failed` and is retried — an emergency is never lost because a channel blinked.
- **False positive:** caregiver marks `falso_positivo`; the Evento closes, `confirmadoPor` is recorded, and the
  fact feeds the per-command false-positive rate used for sensitivity tuning.

## Internal Boundaries (hexagonal — ADR-001 shared)
- **api (inbound adapters):** REST controllers — report ingress, confirmation, event query; DTO mapping;
  authN/Z (device token for ingress, caregiver session for confirmation).
- **application (use cases / ports):** `IngestRecognizedReport`, `GenerateEvento`, `OrchestrateNotification`,
  `ConfirmEvento`. Defines outbound ports: `GestValidationPort`, `CommandLookupPort` (PAC), `CaregiverRoutingPort`
  (ACS), `NotificationDispatchPort` (DASH), `AuditTrailPort` (HIST), `IdempotencyStore`.
- **domain (pure):** `Evento` aggregate, `NotificationDispatch` entity, value objects (`Urgencia`,
  `FeedbackVerdict`, `TimingWindow`), transition rules and invariants. No framework, no I/O.
- **infrastructure (outbound adapters):** JPA repositories (tenant-scoped), REST clients (Feign) for
  GEST/PAC/ACS/DASH/HIST, the idempotency ledger store, and notification-dispatch persistence.

## Cross-cutting Concerns
- **Security:** ingress authenticated by a device-scoped token (shape jointly owned with EDGE — open, doc 06);
  confirmation authenticated by the caregiver session and authorized against the patient's tenant + role (ACS).
- **Multi-tenancy (ADR-003 shared):** every request carries/resolves the `empresa`; all persistence and lookups
  are schema-scoped (`t_<empresa>`). No cross-tenant read is possible.
- **Observability:** each report/event carries a correlation id; created/notified/confirmed transitions are
  logged and mirrored to HIST; delivery status of every notification is inspectable.
- **Reliability:** idempotent ingress, durable Evento before notification, retryable dispatch with recorded
  status — the design assumes edge retries and transient downstream outages.
- **Privacy (LGPD hard rule):** no image/frame/landmark is accepted or stored — only primitive ids, a scalar
  confidence, timing metadata, and business references.
- **Resilience of sync calls:** GEST/PAC/ACS lookups have timeouts and fail-closed for emergencies (an
  unvalidatable emergency is surfaced, never silently dropped); notification dispatch fails **open** to a
  retry queue so the Evento is never lost.
