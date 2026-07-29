<!-- OUTPUT-SCHEMA
- Stack
- Service Boundary
- Package Organization
- Hub Service Dependencies
- External Integrations
- Async vs Sync Decisions
-->

# 04 — Backend Architecture Direction

## Stack
- **Language / runtime:** Java 21.
- **Framework:** Spring Boot 3.5.x (Web, Validation, Data JPA).
- **Persistence:** PostgreSQL 15+, schema-per-tenant (`neuroassist_global` + per-empresa schemas).
- **Migrations:** centralized Flyway via the `neuroassist-ai-migrations` (MIG) service — PAC does not
  run its own migrator (see doc 05).
- **Service discovery / routing:** Eureka (REG) + Spring Cloud Gateway (GWY).
- **Messaging:** none — `stack.messaging.enabled = false`; all inter-service communication is
  synchronous REST.
- **AI:** none — `ai.hasAi = false`.

## Service Boundary
`pac-service` is a **single independent microservice** (ADR-002 shared, independent microservices),
deployed on its own, registered in Eureka and routed via GWY. It is the system of record for `Paciente`
and `Comando`. It is intentionally *not* decomposed further: patient registration and command
authoring/calibration share the same aggregates, transactions and tenant context; splitting them would
add network hops without a bounded-context seam. GEST (detectability/profiles), EVNT (events), ACS
(identity), EDGE (devices) are separate services PAC calls by reference.

## Package Organization
Hexagonal (ADR-001 shared), package base `com.neuroassist.pac`:
- **`api`** — REST controllers, request/response DTOs, bean-validation, RFC 7807 (`ProblemDetail`) error
  handling. Two surfaces: the authoring surface (DASH/GWY) and the read surface (GEST/EVNT/DASH consume
  the active catalog + patient).
- **`application`** — use cases (register patient; author/edit/retire command; calibrate; run eligibility
  gate; publish catalog), transaction boundaries, and the `Comando` lifecycle state machine. Depends on
  domain + outbound ports only.
- **`domain`** — `Paciente`, `Comando` aggregates; `Sensibilidade`, `TimingWindow`, `Urgencia`,
  `GestureRef` value objects; invariants and lifecycle. Pure Java, no Spring/JPA imports.
- **`infrastructure`** — JPA entities/repositories (schema-per-tenant resolution), inbound REST adapters,
  and **outbound port adapters**: `GestDetectabilityPort` (eligibility check), `AcsDirectoryPort`
  (tenant/caregiver resolve), `EdgeDevicePort` (device resolve), implemented as REST clients over
  Eureka/GWY.

## Hub Service Dependencies
- **TechLab AI:** none. The module consumes no orchestration, knowledge, document_composition or
  analytics capability — command meaning and calibration are explicit family decisions, not inference.

## External Integrations
- **GEST (`gest-service`)** — `GestDetectabilityPort`: resolve `TipoGesto` by id and check `supported`
  state for the emergency-eligibility gate; downstream, GEST reads PAC's active calibrated catalog to
  build detection profiles. Synchronous REST.
- **ACS (`acs-service`)** — `AcsDirectoryPort`: resolve `Tenant` (empresa) validity and the responsible
  `Cuidador`(es) linked to a patient. Synchronous REST.
- **EDGE (`edge-service`)** — `EdgeDevicePort`: resolve the `DispositivoEdge` a patient is bound to.
  Synchronous REST.
- **No external third-party APIs** in the MVP; no biometric/image ingress at any boundary (LGPD).

## Async vs Sync Decisions
- **All cross-service calls are synchronous REST** (messaging disabled). The emergency-eligibility gate
  is a *blocking, fail-closed* call to GEST: a command with `urgencia = emergencia` is **not published**
  on a timeout or unknown result. Routine commands treat the gate as advisory (warn, don't block).
- **Downstream propagation is pull-based, not push:** PAC does not emit a bus event when a catalog
  changes; GEST pulls the active calibrated catalog (or is signaled synchronously) to republish a
  `DetectionProfile`. Audit-worthy facts (`ComandoPublicado`, `ComandoRecalibrado`, `ComandoAposentado`)
  are sent to HIST synchronously.
- **Reevaluation trigger:** if the pilot grows to many patients/edges, the pull/synchronous model is the
  first candidate to move behind a broker (RabbitMQ) — recorded as a known future decision, not MVP work.
