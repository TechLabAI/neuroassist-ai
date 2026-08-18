<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
`dash-service` is an independent Spring Boot microservice (ADR-002 shared) that owns exactly one thing: the
**Backend-for-Frontend of the caregiver web panel**. Inside the boundary are the caregiver authentication/
scoping filter, the read-composition use cases that fan out to upstream services and shape panel view models,
the **inbound notification endpoint** EVNT dispatches to, the **server-push (SSE) channel** that carries
notifications to the browser, the on-connect catch-up, and the **write-through proxies** for calibration,
confirmation and acknowledgement. Outside the boundary are every source of truth DASH reads or writes through
— HIST (history), PAC (commands/calibration), EVNT (events/notifications), EDGE (device liveness), ACS
(identity/linkage) — and all infrastructure services (GWY routing, REG discovery, MIG migrations). DASH holds
no durable domain state: it never becomes a place other services read business truth from.

## External Actors and Systems
- **Caregiver's browser (Angular panel)** — the human client. Authenticates to DASH (via GWY), fetches
  composed view models, holds an SSE connection for live notifications, and posts caregiver actions.
- **EVNT (`evnt-service`)** — dual role. **Inbound:** dispatches `NotificationDispatch` to DASH (DASH is
  EVNT's delivery target, EVNT ADR-005). **Outbound (read/write from DASH):** source of event/alarm state and
  feedback; DASH pulls unacknowledged notifications on connect and posts `delivered`/`acknowledged` and event
  confirmations back (EVNT ADR-006).
- **PAC (`pac-service`)** — read source for patient + command catalog and current calibration; write target
  for proxied calibration edits (PAC is the authoring source, PAC ADR-004).
- **HIST (`hist-service`)** — read source for the audit/history timeline rendered on "Histórico".
- **EDGE (`edge-service`)** — read source for device online/offline liveness, polled by DASH on the
  established `DASH poll 15s` ETag/304 contract.
- **ACS (`acs-service`)** — authority for caregiver identity, roles and caregiver↔patient linkage; consulted
  to authenticate and scope every request.
- **GWY (API Gateway)** — routes the browser→DASH traffic and all DASH↔upstream and EVNT→DASH calls.
- **REG (Eureka)** — service discovery; DASH registers and discovers its upstreams.
- **MIG (migrations)** — owns Flyway; materializes DASH's (minimal/optional) schema per tenant (ADR-003
  shared).

## End-to-End Flows
Read paths compose; the notification path pushes and confirms; write paths proxy. All service-to-service
traffic is synchronous REST via GWY; the browser channel is SSE.

### Golden path — live emergency notification reaches the caregiver
1. EVNT commits an `emergencia` `Evento`, resolves the responsible caregiver (ACS) and **dispatches** to DASH:
   `POST /internal/notifications` with `dispatchId`, tenant, target caregiver, `tier=full_screen_alarm`, and
   the event summary.
2. DASH authorizes the dispatch (trusted EVNT caller), locates the caregiver's **open SSE session**, and
   **pushes** the notification to the panel, which renders the full-screen alarm.
3. DASH reports `delivered` back to EVNT (against `dispatchId`); when the caregiver acknowledges in the panel,
   DASH reports `acknowledged` — closing the lifecycle EVNT owns (`queued → delivered → acknowledged`).
4. If the caregiver has **no open session**, DASH returns a "not currently connected" result to EVNT so
   EVNT's fail-open retry keeps the dispatch alive (EVNT ADR-005); DASH does **not** persist an inbox.

### Catch-up — panel (re)connects
1. The panel opens/re-opens its SSE connection to DASH (authenticated caregiver).
2. DASH registers the live session and **pulls the caregiver's currently unacknowledged notifications from
   EVNT**, replaying them onto the channel so nothing raised during the gap is missed.
3. Steady-state live dispatches then arrive via the golden path.

### Read path — compose a panel screen (e.g. patient dashboard)
1. The panel calls `GET /panel/patients/{id}/overview` on DASH (via GWY), authenticated.
2. DASH resolves the caregiver's scope against ACS (may this caregiver see this patient?), then fans out —
   in parallel — to PAC (patient + command catalog + calibration), EVNT (recent/active events + feedback),
   HIST (recent timeline) and EDGE (device liveness).
3. DASH assembles a single panel view model and returns it; each upstream failure degrades that section
   gracefully (see Degradation), never the whole screen.

### Write path — caregiver action (calibration / confirmation / acknowledgement)
1. The panel posts the action to DASH (e.g. `PUT /panel/commands/{id}/calibration`).
2. DASH authorizes (tenant + linkage via ACS), validates the shape, and **proxies** it to the owner: PAC for
   calibration (PAC ADR-004), EVNT for a confirmation `real`/`falso_positivo` (EVNT ADR-006) or an
   acknowledgement.
3. DASH returns the owner's result to the panel; it stores no copy of the written value (ADR-006 local).

### Degradation
- **A read upstream is down** → DASH returns the composed view with that section marked unavailable (partial
  view model), so a HIST outage never blanks the calibration screen and vice-versa.
- **EDGE liveness poll fails** → the device indicator shows "unknown/stale" (honest), using the ETag/304
  cache semantics from EDGE rather than a false "online".
- **DASH itself is down** → the panel is unavailable but no business data is lost (DASH owns none); EVNT's
  dispatch retry holds emergencies until DASH/panel returns.

## Internal Boundaries
Hexagonal layering (ADR-001 shared):
- **api (inbound adapters):** REST controllers for the panel (`/panel/**`), the internal notification sink
  (`/internal/notifications`), and the **SSE endpoint** (`/panel/stream`). DTO validation, caregiver-context
  extraction, auth. No business logic.
- **application (use cases):** `ComposePatientOverview`, `ComposeHistory`, `DeliverNotification`,
  `ReplayUnacknowledged`, `ProxyCalibrationEdit`, `ProxyConfirmation`, `AcknowledgeNotification`. Orchestrate
  the outbound ports and shape view models; own the (short-lived) transactional/coordination unit.
- **domain (core):** thin — panel **view-model** value objects, the caregiver-scope/visibility rule, the live
  **session** concept, notification-tier rendering semantics. No persisted business aggregate. Framework-free.
- **infrastructure (outbound adapters):** REST clients for HIST/PAC/EVNT/EDGE/ACS behind ports, the SSE
  session registry (in-memory), security (OAuth2 resource server), observability, and the optional
  UI-preference store.
- **Ports** invert every dependency: the application depends on `HistoryQueryPort`, `CommandCatalogPort`,
  `EventStatePort`, `DeviceLivenessPort`, `IdentityDirectoryPort`, `NotificationDispatchBackPort` and a
  `PanelSessionPort`; infrastructure implements them.

## Cross-cutting Concerns
- **Security:** every panel request is authenticated (OAuth2 resource server / JWT, aligned with ACS); the
  caregiver identity is the scope for all fan-out and every proxied write. The `POST /internal/notifications`
  endpoint trusts only the EVNT service identity (service-to-service auth), never the browser. DASH performs
  no privilege elevation — it always acts as the caregiver toward read/write upstreams.
- **Observability:** structured logs, metrics and traces correlated by `X-Correlation-Id` propagated to every
  upstream; key signals are notification delivery latency (dispatch→rendered), SSE session count/lifetime,
  undelivered-dispatch rate (caregiver not connected), per-upstream fan-out latency/error rate, and
  view-model degradation rate.
- **Multi-tenancy (ADR-003 shared):** every request is tenant-scoped; the caregiver's `empresa` is resolved
  at request time and carried on every fan-out and proxy. No cross-tenant composition is representable. DASH's
  optional local schema (if any) is per-`empresa`.
- **Resilience:** fan-out reads are parallel with per-upstream timeouts and graceful section-level
  degradation; the notification path fails **open** (unreachable caregiver → reported to EVNT for retry,
  never dropped). No single upstream outage takes the whole panel down.
- **Privacy/LGPD:** DASH handles only structured derived state and references — no biometric/video data ever
  (project-wide hard rule). It persists essentially nothing personal (no durable domain store); any optional
  UI preference is non-sensitive and tenant-scoped.
