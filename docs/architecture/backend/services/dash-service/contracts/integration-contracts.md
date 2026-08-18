# DASH — Integration Contracts (upstream owner services)

> Service: `dash-service`. All integrations are **synchronous REST** via GWY, discovered via REG (Eureka).
> No external third-party systems, no broker, no hub/AI (see `hub-integration.md`). Every upstream call
> carries `X-Correlation-Id` and a service-to-service credential. Each integration is behind an outbound
> **Port** (hexagonal, ADR-001 shared) so the transport never leaks into `application`/`domain`.

## 0. Cross-cutting policy

- **Timeouts (per call):** default `2000ms` connect+read; the SSE dispatch path uses `1000ms` to keep the
  human path fast. Tunable via env (`UPSTREAM_TIMEOUT_MS`).
- **Retry:** at most **1** retry on connection failure / 5xx for **idempotent GETs** (composition reads).
  **Write-throughs are NOT retried by DASH** (the owner service owns idempotency; a blind retry could
  double-apply). Delivery/ack callbacks to EVNT are idempotent on `dispatchId` and MAY retry.
- **Circuit breaker:** per-upstream breaker; when OPEN, the affected read section degrades (§ADR-004) or, for
  a required upstream (PAC/HIST/ACS), the endpoint returns `503 upstream-unavailable`.
- **Degradation matrix (EARS):**
  - WHILE EDGE is unavailable, THE SYSTEM SHALL render device status as `unknown`/`degraded` (soft-degrade).
  - WHILE EVNT read is unavailable, THE SYSTEM SHALL omit event/notification sections and flag `degraded`.
  - IF ACS is unavailable, THEN THE SYSTEM SHALL respond `503` (no scope ⇒ no authorized view — hard).
  - IF PAC or HIST is unavailable for their own screen, THEN THE SYSTEM SHALL respond `503` for that screen.

## 1. ACS (`acs-service`) — identity + scope  [Port: `IdentityDirectoryPort`]

- **Auth (JWT validation):** local — DASH validates panel JWTs against ACS's published **JWK set**
  (`GET /.well-known/jwks.json`), fetched and cached; no per-request ACS call for signature check.
- **Scope resolution:** `GET /caregivers/{caregiverId}/scope` → `{ tenantId, patientIds: [...] }`. Cached
  per caregiver with short TTL (`SCOPE_CACHE_TTL_SECONDS`, default 30s).
- **Criticality:** hard dependency.
- **Invariant (EARS):** THE SYSTEM SHALL resolve caregiver↔patient scope from ACS before composing any view
  or proxying any write; IF scope cannot be resolved, THEN THE SYSTEM SHALL respond `503`.

## 2. PAC (`pac-service`) — commands + calibration  [Port: `CommandCatalogPort`]

- **Read:** `GET /patients/{patientId}/commands` → command catalog + current calibration (label, `urgencia`,
  `sensibilidade`, min/max gesture ms).
- **Write-through:** `PUT /patients/{patientId}/commands/{commandId}/calibration` (PAC ADR-004; PAC is the
  authoring source). DASH forwards the caregiver edit; returns PAC's authoritative result.
- **Criticality:** required for the overview + calibration screens.
- **Invariant (EARS):** THE SYSTEM SHALL forward calibration edits to PAC unchanged after scope validation
  and SHALL relay PAC's ProblemDetail (same status) on rejection.

## 3. EVNT (`evnt-service`) — events + notifications  [Ports: `EventStatePort`, `NotificationDispatchBackPort`]

- **Inbound (dispatch sink):** EVNT calls DASH `POST /internal/notifications` (see `event-contracts.md`).
- **Read (unacknowledged):** `GET /caregivers/{caregiverId}/notifications?state=unacknowledged` — on-connect
  catch-up.
- **Read (event state):** `GET /patients/{patientId}/events?...` — latest event/feedback for composition.
- **Callbacks:** `POST /dispatches/{dispatchId}/delivered`, `POST /dispatches/{dispatchId}/acknowledged`
  (idempotent on `dispatchId`).
- **Write-through (confirmation):** `POST /events/{eventId}/feedback` `{ feedback: real|falso_positivo }`
  (EVNT ADR-006).
- **Criticality:** required for notification delivery + confirmation; the read section soft-degrades.
- **Invariant (EARS):** THE SYSTEM SHALL report `delivered`/`acknowledged` to EVNT idempotently by
  `dispatchId`; IF the caregiver has no open session, THEN THE SYSTEM SHALL report `no_open_session` so EVNT
  retries.

## 4. HIST (`hist-service`) — history/audit  [Port: `HistoryQueryPort`]

- **Read:** `GET /patients/{patientId}/audit?type=&actor=&from=&to=&page=&size=` → paged audit timeline.
- **Criticality:** required for the "Histórico" screen (hard-degrade to `503` if down).
- **Invariant (EARS):** THE SYSTEM SHALL forward filters/paging to HIST unchanged and SHALL cap `size` at
  `100`.

## 5. EDGE (`edge-service`) — device liveness  [Port: `DeviceLivenessPort`]

- **Read (poll):** `GET /devices?patientId={id}` with **conditional request** (`If-None-Match: <etag>`);
  EDGE answers `200` + `ETag` or `304 Not Modified`. DASH polls on the established `DASH poll 15s` cadence.
- **Staleness:** if the last successful poll is older than `EDGE_STALENESS_SECONDS` (default 45s), status is
  reported as `unknown` + `degraded` (never asserted `offline` from staleness alone).
- **Criticality:** soft dependency (degrades its own indicator).
- **Invariant (EARS):** WHEN EDGE returns `304`, THE SYSTEM SHALL serve the last known liveness; WHILE EDGE
  is stale beyond the threshold, THE SYSTEM SHALL report `unknown`/`degraded`.

## 6. GWY / REG

- **GWY** (`neuroassist-ai-api-gateway`) is the edge for browser→DASH and DASH→upstream traffic.
- **REG** (`neuroassist-ai-service-registry`, Eureka) provides discovery; DASH registers on boot.
- **Invariant (EARS):** THE SYSTEM SHALL register with REG on startup and route upstream calls via GWY/REG
  (no hard-coded host in production config).

## 7. Port ↔ upstream map (for scaffold)

| Outbound Port | Upstream | Adapter class (infrastructure) |
|---|---|---|
| `IdentityDirectoryPort` | ACS | `AcsRestAdapter` |
| `CommandCatalogPort` | PAC | `PacRestAdapter` |
| `EventStatePort` | EVNT | `EvntEventRestAdapter` |
| `NotificationDispatchBackPort` | EVNT | `EvntDispatchCallbackAdapter` |
| `HistoryQueryPort` | HIST | `HistRestAdapter` |
| `DeviceLivenessPort` | EDGE | `EdgeLivenessRestAdapter` |
