# DASH — Event Contracts

> Service: `dash-service` · Messaging: **disabled** (`stack.messaging.enabled = false`).
> There is **no broker**. The "events" below are (a) **synchronous REST contracts** between EVNT and DASH,
> and (b) **internal / caregiver-facing signals**. If a broker is later introduced project-wide, the
> EVNT↔DASH signals become the natural topics with no change of meaning (ADR-005 broker-deferred).

## 1. Transport summary

| Direction | Channel | Transport |
|---|---|---|
| EVNT → DASH (dispatch) | `POST /internal/notifications` | synchronous REST via GWY (service-to-service auth) |
| DASH → EVNT (delivery/ack) | REST callbacks | synchronous REST via GWY/REG |
| DASH → browser (push) | `/panel/stream` | Server-Sent Events (SSE); long-poll fallback |
| DASH → PAC / EVNT (write-through) | REST | synchronous (see integration-contracts.md) |

## 2. Consumed: `NotificationDispatch` (EVNT → DASH)

EVNT owns the notification's existence; DASH owns its **delivery to a human** (anchor event
"Notificação entregue ao cuidador").

**Endpoint:** `POST /internal/notifications`
**Payload:**
```json
{
  "dispatchId": "d-77c1...",
  "tier": "full_screen_alarm",
  "tenantId": "e1...",
  "targetCaregiverId": "c1...",
  "patientId": "p1...",
  "commandLabel": "Emergência",
  "urgencia": "emergencia",
  "raisedAt": "2026-08-17T00:03:55Z"
}
```
**Headers:** `X-Correlation-Id`, service-to-service `Authorization` (`SVC_EVNT`).

- **Idempotency:** key = `dispatchId`. Window = the lifetime of the in-memory dispatch (until acknowledged
  or session gone). A redelivered `dispatchId` MUST NOT produce a second `delivered` callback.
- **Behavior (EARS):**
  - WHEN a `NotificationDispatch` arrives for a caregiver with an open session, THE SYSTEM SHALL render it
    over SSE and emit `delivered(dispatchId)`.
  - IF the caregiver has no open session, THEN THE SYSTEM SHALL respond `no_open_session` and emit **no**
    `delivered` signal (EVNT retries — fail-open).
  - THE SYSTEM SHALL map `urgencia = emergencia → tier full_screen_alarm` and `rotina → discreet` **only as
    supplied by EVNT** (DASH never decides tier — EVNT owns the `urgencia → tier` policy).

## 3. Produced (reported back to EVNT): delivery signals

DASH produces the *signal*; EVNT records the *state*.

### 3.1 `delivered(dispatchId)`
- **When:** the notification was rendered on an open panel.
- **Transport:** REST callback to EVNT (`NotificationDispatchBackPort`).
- **Idempotency:** keyed by `dispatchId`; a retried callback never double-counts.

### 3.2 `acknowledged(dispatchId)`
- **When:** the caregiver acknowledged (`POST /panel/notifications/{dispatchId}/acknowledge`).
- **Transport:** REST callback to EVNT.
- **Idempotency:** keyed by `dispatchId`; repeated acknowledge is a no-op returning success.

- **Invariants (EARS):**
  - THE SYSTEM SHALL emit `delivered`/`acknowledged` **only** for a notification that actually reached an
    open panel / was actually acknowledged (never fabricate delivery to silence EVNT retry).
  - THE SYSTEM SHALL key both callbacks by `dispatchId` for idempotency.

## 4. Consumed on connect: unacknowledged replay (DASH → EVNT read)

**When:** a panel (re)connects (`GET /panel/stream` or `GET /panel/notifications/unacknowledged`).
DASH pulls the caregiver's currently unacknowledged notifications from EVNT and replays them over SSE, then
emits `catchup-complete`.

- **Invariant (EARS):** WHEN a panel reconnects, THE SYSTEM SHALL pull unacknowledged notifications from
  EVNT and SHALL NOT persist them locally (no inbox in DASH).

## 5. Internal / caregiver-facing signals (in-memory only)

Not broker messages; drive DASH's own behavior:

| Signal | Meaning | Effect |
|---|---|---|
| `PanelSessionOpened` | SSE session registered | triggers on-connect catch-up |
| `PanelSessionClosed` | disconnect / heartbeat timeout | evict session; future dispatches → `no_open_session` |
| `ActionProxied` | a caregiver write forwarded to owner | trace only; DASH stores no value |

## 6. Versioning

- **Dispatch payload** is versioned by EVNT; DASH tolerates unknown additive fields (forward-compatible
  deserialization) and requires only `dispatchId`, `tier`, `targetCaregiverId`, `patientId`.
- **Callbacks** (`delivered`/`acknowledged`) carry only `dispatchId` + signal type — stable, minimal.

## 7. Broker-deferred note (ADR-005)

Messaging is disabled in the MVP. If a broker is later introduced project-wide, `NotificationDispatch`
(EVNT → DASH) and the `delivered`/`acknowledged` callbacks (DASH → EVNT) become the natural topics
**without changing their meaning or idempotency key** (`dispatchId`). The browser channel remains SSE
regardless (a broker is server-side; the panel push contract does not change).
