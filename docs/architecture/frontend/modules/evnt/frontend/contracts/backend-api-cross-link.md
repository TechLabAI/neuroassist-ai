# Backend API Cross-Link — EVNT (Frontend ↔ `evnt-service`)

> This file maps every FE data-access operation (SPEC-04) to the frozen BE api-contract. The BE contract
> is **authoritative** (M1.5 rule). FE never re-defines shapes — it consumes and maps to `*Vm`.
> BE source: `be-neuroassist-ai/docs/architecture/frontend/modules/evnt/evnt-service/contracts/api-contract.md`

## 1. Endpoint cross-link

| FE operation (SPEC-04) | BE endpoint (api-contract) | Auth | FE consumes |
|---|---|---|---|
| `EvntClient.openStream()` | `GET /api/v1/events/stream` (SSE) §2.4/§3.3 | caregiver JWT (`evnt:read`) | `evento.created`, `evento.updated`, `keepalive` frames |
| `EvntClient.listActive(since?)` | `GET /api/v1/events/active` §2.3/§3.2 | caregiver JWT | `ActiveEventsDto` (hydration + poll) |
| `EvntClient.get(id)` | `GET /api/v1/events/{eventoId}` §2.2 | caregiver JWT | `EventoDto` |
| `EvntClient.confirm(id, verdict)` | `POST /api/v1/events/{eventoId}/confirmation` §2.5/§3.4 | caregiver JWT (`evnt:confirm`) | `ConfirmationResponseDto` |
| `EvntClient.ack(id, dispatchId)` | `POST /api/v1/events/{eventoId}/dispatches/{dispatchId}/ack` §2.6 | caregiver JWT | ack status |
| `EvntClient.query(params)` | `GET /api/v1/events` §2.7 | caregiver JWT | paged `EventoDto[]` + `falsePositiveRate` |

## 2. Live-delivery reconciliation (closes FE ADR-005)

The FE learns about new events via **EVNT's own read API**, not a broker (`stack.messaging.enabled=false`):

- **Primary:** `GET /events/stream` (SSE). Each frame carries `id:` = monotonic per-tenant sequence
  (`evento.seq`); reconnect resumes via `Last-Event-ID` (BE api-contract §3.3).
- **Hydration + fallback:** `GET /events/active` on load and while degraded (BE api-contract §3.2).

This is exactly the shape FE ADR-005(b) depends on. FE `deliveryMode ∈ {live, degraded}` maps to
"stream open" vs "polling `/events/active`".

## 3. Path reconciliation note

FE vision draft said `POST /events/{id}/confirm`; the **authoritative BE path is
`POST /events/{eventoId}/confirmation`** (§3.4). SPEC-04 and generator-config.yml adopt the BE path.

## 4. Error contract cross-link

FE `AppError` maps 1:1 from BE `ProblemDetail` `type` values (BE api-contract §6). See SPEC-07 for the
FE surface (inline/banner/toast/modal) per code. Caregiver-facing subset:
`feedback-terminal (409)`, `event-not-found (404)`, `tenant-mismatch (403)`, `forbidden (403)`,
`unauthenticated (401)`, `validation (400)`, `upstream-unavailable (502/504)`.

## 5. Open items inherited from BE (backlog)

- **Device-token auth shape** (BE gap #1) — ingress only; does not affect the caregiver FE, but the
  degraded/error copy must not assume a specific auth outcome.
- Emergency escalation (ACS, gap #3) and false-positive-rate consumer (gap #5) — surfaced by BE `GET /events`
  aggregate; FE history view may render `falsePositiveRate` once the consumer is defined.
