<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
`pac-service` is an independent Spring Boot microservice (ADR-002 shared) that owns two aggregates —
`Paciente` and `Comando` — and the authoring/calibration logic around them. **Inside the boundary:**
patient registration, command catalog CRUD, per-command calibration (sensitivity, timing, urgency), the
emergency-eligibility gate (calling GEST), the command lifecycle (drafted → validated → published →
recalibrated → retired), and the read API that exposes a patient's active calibrated catalog.
**Outside the boundary:** computer vision and primitive detectability (GEST), detection-profile
publication (GEST), event generation and notification (EVNT), caregiver identity/auth (ACS), device
pairing/heartbeat (EDGE), audit storage (HIST), UI rendering (DASH). PAC references those by id and
resolves them synchronously over REST.

## External Actors and Systems
- **Family caregiver (Jamilly)** — via DASH/GWY, authors patients and commands and adjusts calibration.
- **Technical conductor (Danilo)** — seeds catalogs and validates emergency eligibility during tuning.
- **GWY (API Gateway)** — the single ingress; routes authenticated calls to `pac-service`.
- **REG (Eureka)** — service discovery for PAC's synchronous calls to GEST/ACS/EDGE.
- **GEST (`gest-service`)** — resolves/validates `TipoGesto` detectability; downstream, snapshots PAC's
  per-command calibration into a `DetectionProfile`.
- **ACS (`acs-service`)** — resolves `Tenant` (empresa) and responsible `Cuidador`(es).
- **EDGE (`edge-service`)** — resolves the patient's `DispositivoEdge`.
- **EVNT / DASH / HIST** — downstream readers of the `Comando` catalog and `Paciente` record.

## End-to-End Flows
**Golden path — publish an emergency command:**
1. Caregiver (via DASH→GWY) requests to create a `Comando` for a patient, choosing gesture
   (primitive id(s)), free-text label/need, `sensibilidade`, timing window and `urgencia = emergencia`.
2. PAC validates the payload and the patient's tenant scope, persisting the command as `drafted`.
3. PAC calls **GEST** synchronously to confirm every referenced primitive is `supported`
   (emergency-eligibility gate, ADR-005). If any is not, the command stays `drafted` and PAC returns a
   descriptive RFC 7807 error — it is never published.
4. On success PAC transitions the command `drafted → validated → published`, making it part of the
   patient's active catalog.
5. **GEST** later pulls the patient's active calibrated catalog and snapshots its params into a new
   `DetectionProfile` version the edge consumes.

**Alternative — routine command (fast path):**
- Same as above for `urgencia = rotina`, except the GEST eligibility gate is advisory (a non-`supported`
  primitive yields a warning, not a hard rejection — routine misses are tolerable, emergencies are not).

**Alternative — recalibrate a published command:**
- Caregiver adjusts one command's `sensibilidade`/timing; PAC applies the change in isolation (ADR-004),
  bumps the command's calibration version, and re-runs the eligibility gate if urgency is `emergencia`.
  Sibling commands are untouched; the change signals GEST to republish the profile.

**Alternative — edit/retire a referenced command:**
- Editing a command that historical `Evento`s reference does not mutate the referenced snapshot; PAC
  versions or retires the command (ADR-006), preserving EVNT/HIST interpretability.

## Internal Boundaries
Hexagonal (ADR-001 shared), four layers:
- **api** — REST controllers, DTOs, request validation, RFC 7807 error handling; the DASH/GWY-facing
  surface plus the read endpoints GEST/EVNT/DASH consume.
- **application** — use-case orchestration: register patient, author/edit/retire command, calibrate,
  run the emergency-eligibility gate, publish catalog. Owns transactions and the lifecycle state machine.
- **domain** — `Paciente` and `Comando` aggregates, calibration value objects (`Sensibilidade`,
  `TimingWindow`, `Urgencia`), lifecycle states and invariants; pure, no framework/IO.
- **infrastructure** — JPA persistence (schema-per-tenant), and **outbound ports/adapters** to GEST
  (detectability check), ACS (tenant/caregiver resolve) and EDGE (device resolve) over REST via
  Eureka/GWY. Detectability and identity are consumed through ports, never re-owned.

## Cross-cutting Concerns
- **Security & multi-tenancy** — every request is authenticated at GWY and tenant-scoped; PAC enforces
  `empresa` isolation on every `Paciente`/`Comando` read and write (schema-per-tenant, ADR-003).
- **LGPD / privacy** — PAC persists only catalog metadata and primitive ids; no image, frame or facial
  landmark is ever accepted or stored (project-wide hard rule).
- **Observability** — structured logs, health/readiness endpoints, and correlation-id propagation on the
  synchronous calls to GEST/ACS/EDGE so a failed eligibility gate is traceable end to end.
- **Resilience** — GEST/ACS/EDGE calls are synchronous (messaging disabled); PAC applies timeouts and
  clear failure semantics (a command is never published on an *unknown* eligibility result — fail closed
  for emergencies).
- **Consistency** — calibration edits are isolated per command (no shared mutable state); command
  lifecycle transitions are transactional and audited (facts emitted to HIST).
