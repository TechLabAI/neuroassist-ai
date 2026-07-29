# SPEC-03 — Live Event Delivery Channel (SSE + polling fallback)

> Service: `evnt-service` · **Closes the M1 handoff open item and FE ADR-005(b).**
> Sub-feature critical to the alarm guarantee: how a new event reaches the caregiver's browser with
> messaging disabled and no broker. Canonical shape: `contracts/api-contract.md` §3.2/§3.3.

## Objetivo
Specify the browser-facing live channel so the FE `AlarmOutlet` (FE ADR-005) has a frozen backend contract:
SSE `GET /events/stream` as the primary push channel, `GET /events/active` for hydration and the polling
fallback. This resolves the decision the vision left open ("SSE vs polling") — the answer is **both, with a
defined role for each**.

## Escopo
- **In:** SSE endpoint semantics (frames, `id:`/`Last-Event-ID` resume, keepalive, tenant scoping),
  `/events/active` hydration + `since` polling, degraded-mode contract, connection lifecycle server-side.
- **Out:** the FE outlet/overlay (FE ADR-005 owns it), the dispatch record to DASH (SPEC-02), auth (SPEC-06).

## Decisões técnicas
- **Why not a broker:** `stack.messaging.enabled = false` (ADR-005 shared). SSE rides the existing HTTP/GWY
  path, needs no infra, and gives sub-second time-to-alarm; polling is the strict fallback (FE ADR-005
  alternatives 2 & 3 rejected).
- **Resume without gaps:** every Evento carries a monotonic per-schema `seq` (`evento.seq`, SPEC-04). SSE
  frames set `id: <seq>`; a reconnect with `Last-Event-ID` replays all higher `seq` — no active event lost.
- **Fan-out source:** an in-process listener on `EventoCriado`/`EventoConfirmado`/`FalsoPositivoMarcado`
  pushes to a per-tenant registry of open `SseEmitter`s (`EventStreamPublisher` port,
  `SseEventStreamPublisher` adapter).
- **Hydration on load:** the FE fetches `/events/active` once before opening the stream, so a refresh during
  an active alarm re-shows it; `activeEvent` returns the highest-priority unconfirmed `emergencia`.
- **Degraded mode:** if the stream can't open/drops, the client polls `/events/active?since=…`; the server
  guarantees `/events/active` is always available even when streaming isn't (FE `deliveryMode → 'degraded'`).

## Implementação
- `EventStreamController` (`GET /events/stream`, `produces=text/event-stream`) registers an `SseEmitter`
  keyed by `(tenant, caregiverSession)`; honors `Last-Event-ID` header for replay from `evento.seq`.
- `EventQueryController` (`GET /events/active`) reads open events (`feedback='nao_avaliado'` OR open
  dispatch) emergency-first; supports `since` for cheap polls.
- Keepalive scheduler emits a `keepalive` frame every `keepAliveSeconds` (default 15).
- Emitter teardown on completion/timeout/error; the FE tears down on `evnt.bootstrap` destroy (FE ADR-005).

## Casos de borda
- Stream drop mid-alarm → reconnect + `Last-Event-ID` replays the missed `evento.created`.
- Client never opens a stream → all delivery via `/events/active` polls (degraded but complete).
- Two caregivers of the same tenant → both streams receive the frame; cross-tenant streams receive nothing.

## Critérios de aceite (EARS)
- WHEN a new Evento is persisted, THE SYSTEM SHALL emit an `evento.created` SSE frame to every open stream
  of the event's tenant, carrying `id` = the Evento `seq`.
- WHEN a client connects to `/events/stream` with `Last-Event-ID`, THE SYSTEM SHALL replay every frame whose
  `seq` is greater than that id.
- THE SYSTEM SHALL return, from `/events/active`, the highest-priority unconfirmed `emergencia` in
  `activeEvent`, so a client hydrating on load re-shows an in-flight alarm.
- WHILE an open stream is idle, THE SYSTEM SHALL emit a `keepalive` frame every `keepAliveSeconds`.
- IF the SSE stream cannot be established, THEN THE SYSTEM SHALL keep `/events/active` available so the
  client can poll (degraded mode).
- THE SYSTEM SHALL scope every stream strictly to its tenant; no frame SHALL cross an `empresa` boundary.
