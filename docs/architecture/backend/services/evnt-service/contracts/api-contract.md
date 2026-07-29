# EVNT — API Contract (`evnt-service`)

> Module: **EVNT — Eventos e Alarmes** · Service: `evnt-service` · Package base: `com.neuroassist`
> Project: NeuroAssist AI · Architecture: independent microservices (ADR-002 shared) · Messaging: disabled
> Contracts are versionable and testable. Acceptance criteria use EARS notation
> (guide: `docs/architecture/backend/standards/ears-notation.md`).
> Traces ADR-004 (system of record), ADR-005 (urgency-tiered notification), ADR-006 (confirmation feedback).

## 0. Conventions

- **Base path:** `/api/v1` (routed via GWY, discovered via REG). All paths below are relative to it.
- **Media type:** `application/json; charset=utf-8`, except the live stream (`text/event-stream`).
- **Errors:** RFC 7807 `application/problem+json` (see §6).
- **Idempotency:** `reportId` (client-generated UUID) is the idempotency key for ingress (ADR-004).
- **Time:** all timestamps ISO-8601 UTC (`2026-07-28T14:03:11Z`).
- **Tenant:** every request is tenant-scoped; `empresa` is resolved from the JWT `tenant` claim, never
  from a path/body parameter (ADR-003 shared, schema-per-empresa).

## 1. Mandatory headers

| Header | Direction | Rule |
|---|---|---|
| `Authorization: Bearer <jwt>` | request | Device-scoped token for ingress; caregiver session for confirmation/query/stream (§5). |
| `X-Correlation-Id` | request/response | Propagated end-to-end; generated at GWY if absent, echoed on every response and mirrored to HIST. |
| `Idempotency-Key` | request (ingress) | Optional transport-level echo of `reportId`; the body `reportId` is authoritative. |
| `X-Tenant` | internal only | Never trusted from the edge; the `tenant` JWT claim is authoritative. |

**EARS — headers**
- THE SYSTEM SHALL echo the received (or generated) `X-Correlation-Id` on every response and audit fact.
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL accept the value injected by GWY.
- IF the resolved tenant of the JWT differs from any tenant reference in the body, THEN THE SYSTEM SHALL
  reject the request with `403 Forbidden` (`type=/errors/tenant-mismatch`).

## 2. Resource map

| # | Method | Path | Purpose | Auth | Success |
|---|---|---|---|---|---|
| 2.1 | `POST` | `/reports` | Ingress a `RecognizedCommandReport` from the edge (idempotent) | device token | `201` / `200` (dup) |
| 2.2 | `GET` | `/events/{eventoId}` | Fetch a single Evento (snapshot + status) | caregiver | `200` |
| 2.3 | `GET` | `/events/active` | List currently-active (unconfirmed) events for the tenant — hydration + poll fallback | caregiver | `200` |
| 2.4 | `GET` | `/events/stream` | **SSE** live push of new/updated events (primary live channel) | caregiver | `200` (`text/event-stream`) |
| 2.5 | `POST` | `/events/{eventoId}/confirmation` | Caregiver verdict `real` / `falso_positivo` (terminal, audited) | caregiver | `200` |
| 2.6 | `POST` | `/events/{eventoId}/dispatches/{dispatchId}/ack` | Caregiver/DASH acknowledges a delivered notification | caregiver | `200` |
| 2.7 | `GET` | `/events` | Query events (paged, filterable) for history/false-positive rate | caregiver | `200` |
| 2.8 | `GET` | `/health` | Liveness/readiness (see SPEC-07) | none/internal | `200` |

> **Live-delivery reconciliation (closes FE ADR-005 — the M1 handoff open item).**
> The caregiver panel (Angular `@neuroassist/evnt` web lib) learns about new events by consuming
> **EVNT's own read API**, not a broker: `GET /events/stream` (SSE, primary) and `GET /events/active`
> (single-fetch hydration on load + polling fallback when the stream is unavailable). This is the exact
> shape FE ADR-005(b) depends on. The blueprint's "NotificationDispatch → DASH" remains the server-side
> **dispatch decision + delivery record**; the browser-facing *live channel* is these two endpoints. There
> is no broker (ADR-005 shared, `stack.messaging.enabled = false`); SSE rides the existing HTTP/GWY path.

## 3. Endpoint details

### 3.1 `POST /reports` — recognized-command ingress (ADR-004)

**Request body** (`RecognizedCommandReport`, frozen by GEST detection contract §3.1; EVNT does not own it):
```json
{
  "reportId": "8f1d5c1a-3e2b-4a7c-9f10-2b6f0a9c11de",
  "pacienteId": "b21e...",
  "commandRef": "cmd_9a3f...",
  "primitives": [ { "tipoGestoId": "tg_blink_double", "confidence": 0.94 } ],
  "confidence": 0.94,
  "timingWindow": { "startMs": 1730000000000, "endMs": 1730000000850, "durationMs": 850 },
  "recognizedAt": "2026-07-28T14:03:10Z",
  "deviceId": "edge_7c2b..."
}
```

**Response `201 Created`** (new Evento) — `Location: /api/v1/events/{eventoId}`:
```json
{
  "eventoId": "e_44a1...",
  "status": "created",
  "urgencia": "emergencia",
  "feedback": "nao_avaliado",
  "reportId": "8f1d5c1a-3e2b-4a7c-9f10-2b6f0a9c11de",
  "notification": { "tier": "full_screen_alarm", "dispatchId": "d_01...", "status": "queued" },
  "recognizedAt": "2026-07-28T14:03:10Z"
}
```

**Response `200 OK`** (duplicate `reportId`) — same body as the original creation, `status: "duplicate"`,
no side effect.

**Status codes**

| Code | Condition |
|---|---|
| `201` | New report validated (GEST) and Evento persisted; notification queued. |
| `200` | `reportId` already processed — original Evento/verdict returned, no new event. |
| `400` | Malformed body / missing `reportId` / bad timing window. |
| `401` | Missing/invalid device token. |
| `403` | Device token tenant ≠ patient tenant. |
| `422` | GEST rejected the report (unknown profile version / primitive not in profile) — **no Evento created**. |
| `502`/`504` | GEST/PAC unreachable or timed out on the emergency path — surfaced, never silently dropped. |

**EARS — ingress**
- WHEN a `RecognizedCommandReport` with a new `reportId` is received, THE SYSTEM SHALL validate it against
  GEST and, on success, persist exactly one `Evento` before attempting any notification.
- IF a `RecognizedCommandReport` carries a `reportId` already in the idempotency ledger, THEN THE SYSTEM
  SHALL return the original Evento with `200 OK` and produce no new Evento and no new dispatch.
- IF GEST rejects the report, THEN THE SYSTEM SHALL return `422` with a `ProblemDetail` and SHALL NOT
  create an Evento.
- WHILE the request is an `emergencia` and GEST or PAC is unreachable within the timeout, THE SYSTEM SHALL
  return `502`/`504` and record the failure for observability rather than dropping the request.
- THE SYSTEM SHALL reject any body containing image, frame or facial-landmark data with `400`
  (`type=/errors/forbidden-biometric-payload`) — privacy invariant.

### 3.2 `GET /events/active` — active events (hydration + poll fallback)

Returns the tenant's currently-active events: `feedback = nao_avaliado` **or** any Evento with an open
(`queued`/`failed`) dispatch. Ordered emergency-first, then most-recent `recognizedAt`.

**Query params:** `since` (optional ISO-8601 — return events changed after this instant, for cheap polls),
`urgencia` (optional filter).

**Response `200 OK`:**
```json
{
  "serverTime": "2026-07-28T14:03:12Z",
  "activeEvent": { "eventoId": "e_44a1...", "urgencia": "emergencia", "tier": "full_screen_alarm", "feedback": "nao_avaliado", "commandLabel": "Preciso de ajuda", "recognizedAt": "2026-07-28T14:03:10Z" },
  "events": [ /* full active list, emergency first */ ]
}
```

**EARS — active**
- THE SYSTEM SHALL return, in `activeEvent`, the highest-priority unconfirmed `emergencia` event when one
  exists, so a client hydrating on load re-shows an in-flight alarm.
- WHEN `since` is supplied, THE SYSTEM SHALL return only events whose state changed at or after `since`.
- WHILE no event is active for the tenant, THE SYSTEM SHALL return `activeEvent: null` and an empty `events`.

### 3.3 `GET /events/stream` — SSE live channel (primary; closes FE ADR-005b)

`text/event-stream`, one connection per caregiver session, tenant-scoped. Named events:

| SSE `event:` | `data` payload | Emitted when |
|---|---|---|
| `evento.created` | `{eventoId, urgencia, tier, commandLabel, recognizedAt, feedback}` | a new Evento is persisted |
| `evento.updated` | `{eventoId, feedback, confirmadoPor?, confirmadoAt?}` | feedback/dispatch status changes |
| `keepalive` | `{serverTime}` | every `keepAliveSeconds` (default 15) to hold the connection |

**Response headers:** `Content-Type: text/event-stream`, `Cache-Control: no-cache`, `Connection: keep-alive`.
Each SSE frame carries an `id:` equal to a monotonic per-tenant sequence, so a reconnecting client sends
`Last-Event-ID` to resume without gaps.

**EARS — stream**
- WHEN a new `emergencia` Evento is persisted, THE SYSTEM SHALL emit an `evento.created` SSE frame to every
  open stream of the responsible caregiver's tenant within the same request cycle.
- WHEN a client reconnects with `Last-Event-ID`, THE SYSTEM SHALL replay every event with a higher sequence
  so no active event is missed across a drop.
- WHILE a stream is open and idle, THE SYSTEM SHALL emit a `keepalive` frame every `keepAliveSeconds`.
- IF the stream cannot be established, THEN THE SYSTEM SHALL still serve `GET /events/active` so the client
  can fall back to polling (FE `deliveryMode → 'degraded'`).

### 3.4 `POST /events/{eventoId}/confirmation` — caregiver verdict (ADR-006)

**Request:** `{ "verdict": "real" | "falso_positivo" }` (the acting caregiver identity is taken from the
session/JWT and resolved via ACS into `confirmadoPor` — never trusted from the body).

**Response `200 OK`:** `{ "eventoId": "...", "feedback": "real", "confirmadoPor": "cg_...", "confirmadoAt": "..." }`

**Status codes:** `200` set; `404` unknown event/tenant; `409` already-terminal feedback (idempotent
re-submit of the *same* verdict returns `200`, a *different* verdict returns `409`); `422` unresolvable
`confirmadoPor`.

**EARS — confirmation**
- WHEN a caregiver submits a verdict for a `nao_avaliado` event, THE SYSTEM SHALL set `feedback` once,
  record `confirmadoPor` + `confirmadoAt`, and append `EventoConfirmado`/`FalsoPositivoMarcado` to HIST.
- IF a verdict is submitted for an event whose `feedback` is already terminal, THEN THE SYSTEM SHALL return
  `409 Conflict` (a correction is a new audited action, never an in-place overwrite).
- IF `confirmadoPor` cannot be resolved via ACS, THEN THE SYSTEM SHALL reject the transition with `422`.

### 3.5 `POST /events/{eventoId}/dispatches/{dispatchId}/ack`

Marks a `delivered` dispatch `acknowledged`.
- WHEN a delivered dispatch is acknowledged, THE SYSTEM SHALL move its status to `acknowledged` and stop retrying it.
- IF the dispatch is not in `delivered`, THEN THE SYSTEM SHALL return `409`.

### 3.6 `GET /events` — history / false-positive rate

Paged (`page`, `size`), filters: `pacienteId`, `comandoRef`, `feedback`, `urgencia`, `from`, `to`.
Returns a page plus an aggregate `falsePositiveRate` per `comandoRef` when `groupBy=comando` is set
(feeds sensitivity tuning — ADR-006).

## 4. DTO summary

| DTO | Fields (non-exhaustive) |
|---|---|
| `RecognizedCommandReportDto` | `reportId`, `pacienteId`, `commandRef`, `primitives[]`, `confidence`, `timingWindow`, `recognizedAt`, `deviceId` |
| `EventoDto` | `eventoId`, `pacienteId`, `comandoRef`, `commandLabelSnapshot`, `urgenciaSnapshot`, `timingWindow`, `feedback`, `confirmadoPor?`, `confirmadoAt?`, `recognizedAt` |
| `NotificationDto` | `dispatchId`, `tier`, `targetCaregiverId`, `status`, `attempts`, `lastAttemptAt` |
| `ConfirmationRequestDto` | `verdict` |
| `ActiveEventsDto` | `serverTime`, `activeEvent?`, `events[]` |

## 5. AuthN / AuthZ (see SPEC-06)

- **Ingress (`POST /reports`):** device-scoped token. Shape is **jointly owned with EDGE and still open**
  (doc 06 gap #1): assumed JWT-shaped (`deviceId` + `tenant` + `paciente` claims) until EDGE freezes
  mTLS vs. OAuth2 `client_credentials`. SPEC-06 carries the assumed contract + the open item.
- **Caregiver endpoints (`/events/**`, `/events/stream`, confirmation):** OAuth2 resource-server, caregiver
  session JWT. Authorization = tenant match (JWT `tenant` claim = event tenant) **and** the caregiver is a
  routing target/role for the patient (resolved via ACS). Scopes: `evnt:read`, `evnt:confirm`.
- **`X-Correlation-Id`** propagated to every downstream Feign call and audit fact.

**EARS — auth**
- IF a caregiver JWT resolves to a tenant other than the event's tenant, THEN THE SYSTEM SHALL return `403`
  and SHALL NOT reveal whether the `eventoId` exists.
- WHERE the caregiver session lacks scope `evnt:confirm`, THE SYSTEM SHALL reject confirmation with `403`.

## 6. Errors (RFC 7807 `application/problem+json`)

| `type` | HTTP | `title` | When |
|---|---|---|---|
| `/errors/validation` | 400 | Invalid request | Malformed body / bad timing window |
| `/errors/forbidden-biometric-payload` | 400 | Biometric payload rejected | Image/frame/landmark data present |
| `/errors/unauthenticated` | 401 | Missing or invalid token | Absent/invalid JWT/device token |
| `/errors/tenant-mismatch` | 403 | Tenant not allowed | JWT tenant ≠ resource tenant |
| `/errors/forbidden` | 403 | Not a routing target | Caregiver not authorized for patient |
| `/errors/event-not-found` | 404 | Event not found | Unknown `eventoId` in tenant |
| `/errors/gest-rejected` | 422 | Report not valid | GEST rejected the recognized report |
| `/errors/feedback-terminal` | 409 | Feedback already set | Verdict conflicts with a terminal feedback |
| `/errors/confirmador-unresolved` | 422 | Confirmer not resolvable | ACS cannot resolve `confirmadoPor` |
| `/errors/upstream-unavailable` | 502/504 | Upstream unavailable | GEST/PAC/ACS timeout on critical path |

All `ProblemDetail` bodies carry `type`, `title`, `status`, `detail`, `instance`, and `correlationId`.

**EARS — errors**
- THE SYSTEM SHALL express every error as an RFC 7807 `ProblemDetail` including the `correlationId`.
- IF an upstream dependency times out on an `emergencia` path, THEN THE SYSTEM SHALL return `502`/`504`
  and record `NotificacaoFalhou`/upstream-failure for observability rather than returning `200`.

## 7. Versioning

- Path-versioned (`/api/v1`). Additive fields are backward-compatible; a breaking change bumps to `/api/v2`.
- The `RecognizedCommandReport` schema is **owned by GEST**; EVNT pins the accepted version and rejects
  unknown `reportSchemaVersion` with `422` (delegated to GEST validation).

## 8. References

- ADR-004 (system of record / idempotent ingress), ADR-005 (urgency-tiered notification),
  ADR-006 (confirmation & false-positive feedback).
- FE ADR-005 (global alarm outlet + live delivery) — **closed here** by §2.4 / §3.2 / §3.3.
- `contracts/event-contracts.md`, `contracts/data-model.md`, `SPEC-01`, `SPEC-03`, `SPEC-06`.
