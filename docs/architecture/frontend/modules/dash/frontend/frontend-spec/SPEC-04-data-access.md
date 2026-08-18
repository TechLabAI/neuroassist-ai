# SPEC-04 — Data Access — DASH (Frontend)

> **Barrier:** `backend/services/dash-service/contracts/api-contract.md` (the authoritative wire contract).
> All DASH-FE HTTP goes through `@neuroassist/shared/access-http-base` (injects `X-Empresa-Id`); the JWT
> `Authorization` header is applied by the shared auth interceptor. Base surface is `/panel/**` at
> `dash-service` (port 8090 dev), routed to the browser via GWY.
>
> **Endpoint-name reconciliation (warning).** FE vision docs 02/05 and ADR-005 refer to the home aggregate
> logically as `GET /dashboard/summary` and the list as `GET /notifications`. The **wire contract**
> published by `dash-service` names them `GET /panel/summary` and the notification surfaces under
> `/panel/notifications/**` + the SSE `/panel/stream`. **This SPEC binds to the wire contract** (BE
> authoritative); `/dashboard/summary` is retained only as the logical name in the cross-link. See
> `contracts/backend-api-cross-link.md`.

## 1. Services and facades

- `DashApiService` (`libs/dash/data-access`) — thin `HttpClient` wrapper over `access-http-base`; one method
  per BE endpoint; returns DTOs, no mapping.
- `DashHomeFacade` — calls `getSummary()`, maps `SummaryDto → HomeSummaryVm`, holds `summary`/`state` signals.
- `NotificationsFacade` — owns the SSE subscription (`DashStreamClient`), the catch-up pull, the recent list,
  and the read/acknowledge writes; maps `NotificationEnvelope → NotificationItemVm`.
- `DashStreamClient` (`libs/dash/data-access`) — opens/holds `EventSource` to `/panel/stream`, parses
  `notification`/`heartbeat`/`catchup-complete` events, and exposes a stream of envelopes to
  `NotificationsFacade`. Falls back to `GET /panel/notifications/poll` (long-poll) where SSE is unavailable.

**No `BehaviorSubject` in components. No NgRx.** Facades expose Signals only (`.asReadonly()`).

## 2. Master data-access table (aligned to the BE barrier)

| Resource | Endpoint (wire) | Service method | Facade | Operations | Cache? |
|---|---|---|---|---|---|
| HomeSummary | `GET /panel/summary` | `DashApiService.getSummary()` | `DashHomeFacade` | read | no — fresh on each Home entry (FE 05 §3) |
| PatientOverview | `GET /panel/patients/{patientId}/overview` | `getPatientOverview(id)` | `DashHomeFacade` | read | no |
| History | `GET /panel/patients/{patientId}/history` | `getHistory(id, filter)` | (HIST link; DASH read-through) | read (paged) | no |
| Device | `GET /panel/patients/{patientId}/device` | `getDevice(id)` | injected badge (EDGE at shell) | read | short (ETag/304 honored by BE) |
| NotificationStream | `GET /panel/stream` (`text/event-stream`) | `DashStreamClient.open()` | `NotificationsFacade` | subscribe | live (in-memory) |
| PollFallback | `GET /panel/notifications/poll` | `pollNotifications(cursor, wait)` | `NotificationsFacade` | read (long-poll) | live |
| Unacknowledged | `GET /panel/notifications/unacknowledged` | `getUnacknowledged()` | `NotificationsFacade` | read (catch-up) | no |
| Acknowledge | `POST /panel/notifications/{dispatchId}/acknowledge` | `acknowledge(dispatchId)` | `NotificationsFacade` | write-through (idempotent) | n/a |
| Calibration | `PUT /panel/patients/{patientId}/commands/{commandId}/calibration` | `saveCalibration(...)` | (PAC write-through proxy) | write-through (idempotent) | n/a |
| EventConfirm | `POST /panel/events/{eventId}/confirm` | `confirmEvent(eventId, feedback)` | (EVNT write-through proxy) | write-through | n/a |

> DASH-FE performs **no domain writes of its own** — every write is a proxy to the owner service (PAC/EVNT)
> and returns the owner's authoritative result echoed by DASH (ADR-006). `notification.read` is the only
> local, non-durable UI ack.

## 3. DTOs

DTOs live in `@neuroassist/shared/contracts`. Shapes mirror BE api-contract §2.1 (`SummaryDto`), §3.1
(`NotificationEnvelope`: `dispatchId`, `tier`, `patientId`, `commandLabel`, `urgencia`, `raisedAt`),
§5.1 (`CalibrationRequest`: `sensibilidade`, `minGestureMs`, `maxGestureMs`), §5.2 (`ConfirmRequest`:
`feedback`). Facades own the `DTO → VM` mapping; no DTO leaks into a component.

## 4. SSE lifecycle (client)

- On Home/panel mount, `DashStreamClient.open()` connects `/panel/stream`; on the first `catchup-complete`
  event the facade knows the on-connect replay drained.
- `heartbeat` events keep the connection health-checked (BE emits every `HEARTBEAT_SECONDS`); a missed
  heartbeat triggers a bounded reconnect with jitter.
- `notification` (tier `discreet`) events append to the recent list; `full_screen_alarm` tier is **not**
  rendered by DASH — EVNT's overlay owns it (DASH only lists it read-only, FE 02 §5).
- On unmount/logout the client closes the `EventSource` (BE evicts the in-memory `PanelSession`).

## Critérios de aceite (EARS)

- WHEN the caregiver enters Home, THE SYSTEM SHALL call `GET /panel/summary` and render fresh data without
  serving a stale client cache.
- WHEN the panel connects, THE SYSTEM SHALL open the `/panel/stream` SSE channel and pull on-connect
  catch-up from `GET /panel/notifications/unacknowledged`.
- WHEN a `discreet` `notification` event arrives on the stream, THE SYSTEM SHALL append it to the recent
  notification list without a full re-fetch.
- WHILE the SSE channel is unavailable, THE SYSTEM SHALL fall back to `GET /panel/notifications/poll`
  long-poll for notification delivery.
- WHEN the caregiver saves a calibration edit, THE SYSTEM SHALL `PUT` it to
  `/panel/patients/{patientId}/commands/{commandId}/calibration` and IF the proxy call fails, THEN THE
  SYSTEM SHALL surface the owner's error and SHALL NOT store any calibration value locally.
- WHEN the caregiver acknowledges a `dispatchId`, THE SYSTEM SHALL `POST` the acknowledge idempotently and
  treat a repeated acknowledge of the same `dispatchId` as success.
- THE SYSTEM SHALL send `X-Empresa-Id` and the ACS-issued JWT on every `/panel/**` request.
