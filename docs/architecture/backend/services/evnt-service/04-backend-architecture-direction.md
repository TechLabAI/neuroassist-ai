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
- **Language / runtime:** Java 21, Spring Boot 3.5.x.
- **Persistence:** PostgreSQL 15+ (transactional, schema-per-empresa). Spring Data JPA.
- **Service discovery / routing:** Eureka client (REG) + Spring Cloud Gateway (GWY).
- **Inter-service calls:** synchronous REST via Spring Cloud OpenFeign (no broker — `messaging.enabled = false`).
- **Migrations:** centralized Flyway via the MIG service (all schemas) — evnt-service ships migration
  scripts consumed by MIG, it does not run Flyway standalone (ADR-003 shared / MIG local ADR).
- **Messaging:** none in the MVP. Domain events are in-process (`ApplicationEventPublisher`).

## Service Boundary
`evnt-service` is a **single, independent microservice** (no further decomposition — ADR-002 shared).
Its responsibility is the `Evento` lifecycle: ingest → generate → notify → confirm. It is the **system of
record** for events (GEST validates but does not persist recognized reports; HIST stores the audit trail
but is not the operational store). The notification *rendering* lives in DASH; EVNT owns the *decision and
dispatch record*, not the UI.

## Package Organization (hexagonal — ADR-001 shared)
```
com.neuroassist.evnt
├── api              # inbound REST adapters: ingress, confirmation, query controllers; DTOs; auth
├── application      # use cases + outbound ports
│   ├── usecase      # IngestRecognizedReport, GenerateEvento, OrchestrateNotification, ConfirmEvento
│   └── port         # GestValidationPort, CommandLookupPort, CaregiverRoutingPort,
│                    #   NotificationDispatchPort, AuditTrailPort, IdempotencyStore
├── domain           # Evento aggregate, NotificationDispatch, VOs, transitions, invariants (pure)
└── infrastructure   # JPA repos (tenant-scoped), Feign clients (GEST/PAC/ACS/DASH/HIST),
                     #   idempotency ledger, notification-dispatch persistence, config
```

## Hub Service Dependencies
- **TechLab AI / Hub services:** **none** (`ai.hasAi = false`). Urgency is a deterministic policy on the
  command, not an inference; there is no orchestration/knowledge/document/analytics dependency.

## External Integrations
| Integration | Direction | Transport | Purpose |
|---|---|---|---|
| Edge agent | inbound | REST/HTTPS via GWY (device token) | POST `RecognizedCommandReport` |
| GEST (`gest-service`) | outbound | REST (Feign) | validate report against detection profile/primitives |
| PAC (`pac-service`) | outbound | REST (Feign) | resolve `Comando` (urgency/sensitivity/label) + patient |
| ACS (`acs-service`) | outbound | REST (Feign) | resolve responsible caregiver / routing target |
| DASH (`dash-service`) | outbound | REST (Feign) | dispatch notification (alarm / discreet) |
| DASH → EVNT | inbound | REST | caregiver confirmation callback (`real` / `falso_positivo`) |
| HIST (`hist-service`) | outbound | REST (Feign) | append audit facts (created / confirmed / resolved) |

## Async vs Sync Decisions (high level)
- **Report ingress is synchronous** but **idempotent and durable-first**: EVNT validates (GEST) and persists
  the Evento *before* attempting notification, so a downstream failure never loses the event. `reportId`
  is the idempotency key.
- **Notification dispatch is decoupled from ingress within the process:** the Evento is committed, then
  dispatch is attempted; a failure records a `queued/failed` dispatch and is **retried** (in-process retry
  in the MVP), so an emergency is never lost to a transient DASH/caregiver outage.
- **Lookups (GEST/PAC/ACS) are synchronous REST** with timeouts. For emergencies these calls **fail closed
  and surface** (an unvalidatable/unroutable emergency is escalated/logged, never silently dropped);
  notification delivery **fails open** to the retry queue.
- **Broker deferred:** the in-process domain events (`EventoCriado`, `NotificacaoDespachada`,
  `EventoConfirmado`, …) are named to move onto async topics with **no rename** WHERE a broker is later
  introduced (Framing §11.2 trigger: growth beyond one patient/edge). This is explicitly out of MVP scope
  and consistent with the project-wide `stack.messaging.enabled = false`.
