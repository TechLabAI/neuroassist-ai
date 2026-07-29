# SPEC-04 — Data Access — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · Client: `EvntClient` · Facade: `EventDeliveryFacade`
> (both in `libs/evnt/data-access`). Backend contract is authoritative:
> `be-neuroassist-ai/docs/architecture/frontend/modules/evnt/evnt-service/contracts/api-contract.md`.
> Base path `/api/v1` via `@neuroassist/shared/access-http-base` (adds `X-Empresa-Id` + `Authorization`).
> Acceptance criteria use EARS notation.

## 1. Master resource table

| Resource | Endpoint | Service | Facade | Operations | Cache? |
|---|---|---|---|---|---|
| Live stream | `GET /api/v1/events/stream` (SSE, `text/event-stream`) | `EvntClient.openStream()` | `EventDeliveryFacade.startLiveChannel()` | subscribe (`evento.created`, `evento.updated`, `keepalive`) | no |
| Active events | `GET /api/v1/events/active` | `EvntClient.listActive(since?)` | `EventDeliveryFacade.hydrate()` / poll | list | no (authoritative) |
| Event snapshot | `GET /api/v1/events/{eventoId}` | `EvntClient.get(id)` | `EventDeliveryFacade.loadDetail(id)` | get | no |
| Confirmation | `POST /api/v1/events/{eventoId}/confirmation` | `EvntClient.confirm(id, verdict)` | `EventDeliveryFacade.confirm(id, verdict)` | create (verdict) | no |
| Dispatch ack | `POST /api/v1/events/{eventoId}/dispatches/{dispatchId}/ack` | `EvntClient.ack(id, dispatchId)` | `EventDeliveryFacade.ack(...)` | create | no |
| Event history | `GET /api/v1/events` (paged, filterable) | `EvntClient.query(params)` | `EventDeliveryFacade.loadHistory(params)` | query | no |

> **Path reconciliation (BE authoritative, M1.5).** The FE vision draft referenced `POST /events/{id}/confirm`;
> the frozen BE api-contract §3.4 uses **`POST /events/{eventoId}/confirmation`**. This spec adopts the BE path.

## 2. DTOs consumed (from BE api-contract §4)

```ts
// wire DTOs (mirror BE; FE never re-defines, only maps to *Vm)
export interface EventoDto {
  eventoId: string; pacienteId: string; comandoRef: string;
  commandLabelSnapshot: string; urgenciaSnapshot: 'emergencia' | 'rotina';
  timingWindow: { startMs: number; endMs: number; durationMs: number };
  feedback: 'nao_avaliado' | 'real' | 'falso_positivo';
  confirmadoPor?: string; confirmadoAt?: string; recognizedAt: string;
}
export interface ActiveEventsDto {
  serverTime: string;
  activeEvent: EventoSummaryDto | null;
  events: EventoSummaryDto[];   // emergency-first
}
export interface EventoSummaryDto {
  eventoId: string; urgencia: 'emergencia' | 'rotina'; tier: 'full_screen_alarm' | 'discreet';
  commandLabel: string; feedback: 'nao_avaliado' | 'real' | 'falso_positivo'; recognizedAt: string;
}
export interface ConfirmationRequestDto { verdict: 'real' | 'falso_positivo'; }
export interface ConfirmationResponseDto { eventoId: string; feedback: 'real' | 'falso_positivo'; confirmadoPor: string; confirmadoAt: string; }
```

DTO → `EventoVm` mapping is done in `EventDeliveryFacade` (SPEC-03). Mapping never fabricates
`patientName` — it is resolved via composed page state / summary payload; missing ⇒ fall back to id.

## 3. Live channel (SSE) behavior

- `openStream()` uses `EventSource`-style consumption over `access-http-base` (JWT-authorized).
- Named SSE events → facade mutations:
  - `evento.created` → if `urgencia==='emergencia'` set `activeEvent`; if `rotina` push to `routineQueue`.
  - `evento.updated` → patch matching `EventoVm` (feedback/confirmadoPor); clears `activeEvent` when it
    becomes terminal.
  - `keepalive` → no state change (liveness only).
- Each frame's `id:` is retained; on reconnect the client sends `Last-Event-ID` to resume via
  `evento.seq` (no gap). Teardown on `evnt.bootstrap` destroy.

## 4. Degradation / polling fallback

- IF the stream errors or cannot open, the facade sets `deliveryMode='degraded'` and starts polling
  `GET /events/active?since=<lastSeen>` every `pollIntervalMs` (feature flag `EVNT_POLL_INTERVAL_MS`).
- On stream reconnect, `deliveryMode='live'` and polling stops.
- Hydration on (re)load: one `GET /events/active` **before** opening the stream.

## 5. Optimistic vs safe operations

- **`confirm(id, verdict)` is NOT optimistic** — a safety invariant. The `activeEvent` is cleared only
  **after** the POST succeeds. While in flight, `confirming=true`, control disabled.
- **Routine `dismiss`** IS client-only optimistic — removes from `routineQueue`, no server round-trip.

## 6. Error mapping (see SPEC-07 for full table)

`ProblemDetail` → `AppError` via `@neuroassist/shared/error-model`. `409 /errors/feedback-terminal` is
treated as success-equivalent for confirmation (already confirmed by someone).

## Criterios de aceite (EARS)

- WHEN the module boots, THE SYSTEM SHALL fetch `GET /events/active` to hydrate `activeEvent` before
  opening the live stream.
- WHEN an `evento.created` SSE frame with `urgencia = emergencia` arrives, THE SYSTEM SHALL set
  `activeEvent` to that event so the alarm renders.
- WHEN an `evento.created` SSE frame with `urgencia = rotina` arrives, THE SYSTEM SHALL append it to
  `routineQueue` and SHALL NOT set `activeEvent`.
- WHEN a caregiver confirms attendance, THE SYSTEM SHALL POST to `/events/{id}/confirmation` and SHALL
  clear `activeEvent` only after a `200` response.
- IF the confirmation POST fails with a network/5xx error, THEN THE SYSTEM SHALL keep `activeEvent`
  non-null and surface an inline retry (no silent clear).
- IF the live stream drops, THEN THE SYSTEM SHALL set `deliveryMode='degraded'` and poll `GET /events/active`
  until the stream reconnects.
- WHEN the client reconnects the stream, THE SYSTEM SHALL send `Last-Event-ID` so no active event is
  missed across the drop.
