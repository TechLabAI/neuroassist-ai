# DASH — Event Catalog

> Messaging disabled (no broker). "Events" here are REST-borne signals and internal semantics. See
> `contracts/event-contracts.md` for payloads. `dispatchId` is the correlation/idempotency key throughout.

| Name | Type | Transport | Payload | Known peers |
|---|---|---|---|---|
| `NotificationDispatch` | consumed | REST `POST /internal/notifications` | `event-contracts.md` §2 | EVNT (producer) |
| `delivered(dispatchId)` | produced | REST callback → EVNT | `{ dispatchId }` | EVNT (records state) |
| `acknowledged(dispatchId)` | produced | REST callback → EVNT | `{ dispatchId }` | EVNT (records state) |
| `no_open_session(dispatchId)` | produced | REST response to dispatch | `{ dispatchId, delivery }` | EVNT (retries) |
| `unacknowledged replay` | consumed | REST `GET .../notifications?state=unacknowledged` | list | EVNT (source) |
| `PanelSessionOpened` | internal | in-memory | session tuple | DASH only (drives catch-up) |
| `PanelSessionClosed` | internal | in-memory | sessionId | DASH only (evict) |
| `ActionProxied` | internal | in-memory trace | action ref | DASH only (observability) |
| SSE `notification` | produced | SSE → browser | `NotificationEnvelope` | Angular panel |
| SSE `heartbeat` | produced | SSE → browser | `{ at }` | Angular panel |
| SSE `catchup-complete` | produced | SSE → browser | `{}` | Angular panel |

> Broker-deferred (ADR-005): if messaging is later enabled, `NotificationDispatch` and the
> `delivered`/`acknowledged` callbacks map 1:1 onto topics with the same `dispatchId` idempotency key.
