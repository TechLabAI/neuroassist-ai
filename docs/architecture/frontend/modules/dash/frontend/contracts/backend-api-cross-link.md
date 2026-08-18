# Backend API Cross-Link — DASH (Frontend ↔ dash-service)

> **Barrier (authoritative):** `backend/services/dash-service/contracts/api-contract.md`
> Service `dash-service` · port `8090` (dev) · base surface `/panel/**` (browser) + `/internal/**` (EVNT→DASH).
> Auth: OAuth2 Resource Server (JWT issued by ACS); `X-Correlation-Id` propagated; `X-Empresa-Id` injected by
> the FE shared HTTP interceptor. All browser traffic routed via GWY, discovered via REG.

## 1. Endpoint-name reconciliation (WARNING — read first)

The FE vision docs (`02-architecture-blueprint.md` §1, `05-state-and-data-flow.md` §2) and **ADR-005 (local)**
refer to the home aggregate logically as `GET /dashboard/summary` and the notification list as
`GET /notifications`. The **wire contract published by `dash-service`** uses different paths:

| Logical name (FE vision / ADR-005) | Wire path (BE api-contract — authoritative) |
|---|---|
| `GET /dashboard/summary` | `GET /panel/summary` (§2.1) |
| `GET /notifications` | `GET /panel/notifications/unacknowledged` (§4.1) + SSE `GET /panel/stream` (§3.1) + `GET /panel/notifications/poll` (§3.2) |
| `PATCH /notifications/{id}/read` | `POST /panel/notifications/{dispatchId}/acknowledge` (§4.2) |

**Resolution:** the FE specs bind to the **wire contract** (`/panel/**`) — BE is authoritative. The logical
names survive only as prose in the vision/ADR. Scaffold and implementation MUST use the `/panel/**` paths.

## 2. Consumed endpoints (FE → dash-service `/panel`)

| FE operation | Method + path | BE §  | Notes |
|---|---|---|---|
| Home summary | `GET /panel/summary` | §2.1 | scope from JWT; `degradedSections` drives soft-degrade UI |
| Patient overview | `GET /panel/patients/{patientId}/overview` | §2.2 | PAC+EDGE compose; `503` if PAC down |
| History timeline | `GET /panel/patients/{patientId}/history` | §2.3 | read-through HIST; paged; `size` capped 100 |
| Device liveness | `GET /panel/patients/{patientId}/device` | §2.4 | ETag/304, `unknown`+`degraded` past staleness |
| Notification stream | `GET /panel/stream` (`text/event-stream`) | §3.1 | events: `notification`, `heartbeat`, `catchup-complete` |
| Long-poll fallback | `GET /panel/notifications/poll` | §3.2 | `waitSeconds`≤30, opaque `cursor` |
| Catch-up | `GET /panel/notifications/unacknowledged` | §4.1 | on-connect replay; no local persistence |
| Acknowledge | `POST /panel/notifications/{dispatchId}/acknowledge` | §4.2 | idempotent; `403` off-scope |
| Calibration (→PAC) | `PUT /panel/patients/{patientId}/commands/{commandId}/calibration` | §5.1 | write-through; PAC result echoed |
| Event confirm (→EVNT) | `POST /panel/events/{eventId}/confirm` | §5.2 | `real|falso_positivo`; `422` before EVNT call |

## 3. NOT consumed by the browser

- `POST /internal/notifications` (§6.1) — **service-to-service only** (EVNT→DASH dispatch sink, `SVC_EVNT`
  authority). The browser can never reach `/internal/**`. FE does not call it; it is the inbound leg that
  produces the SSE `notification` events the panel receives on `/panel/stream`.

## 4. Error model alignment (BE §7 → FE SPEC-07)

`application/problem+json` `type` suffixes map to FE surfaces per SPEC-07 §2: `#unauthenticated`→redirect,
`#forbidden-scope`→banner, `#not-found`→toast, `#validation`→inline field error, `#upstream-unavailable`→
banner+retry, `#upstream-rejected`→relayed toast. `correlationId` is carried on every AppError.

## 5. Real-time contract (SSE)

`NotificationEnvelope` fields (BE §3.1 / event-contracts §2): `dispatchId`, `tier`
(`full_screen_alarm|discreet`), `patientId`, `commandLabel`, `urgencia`, `raisedAt`. DASH-FE renders only the
`discreet` tier in the notification center; `full_screen_alarm` is EVNT's overlay (DASH lists it read-only).
