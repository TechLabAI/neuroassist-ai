# EDGE — Event Catalog (`edge-service`)

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service`
> **Messaging disabled** (`stack.messaging.enabled = false`): all events below are **in-process** domain
> events (Spring `ApplicationEventPublisher`), not broker messages. Full behavior: `contracts/event-contracts.md`.

| Event | Type | Payload | Known consumers (in-process) | Future topic (deferred) |
|---|---|---|---|---|
| `DispositivoRegistrado` | domain (in-process) | `event-contracts.md §2.1` | audit log, DASH cache hint | `edge.device.registered.v1` |
| `DispositivoPareado` | domain (in-process) | `event-contracts.md §2.1` | audit append, DASH cache hint | `edge.device.paired.v1` |
| `DispositivoDespareado` | domain (in-process) | `event-contracts.md §2.1` | audit append, cache invalidation | `edge.device.unpaired.v1` |
| `HeartbeatRecebido` | domain (anchor, in-process) | `event-contracts.md §2.1` | liveness refresh, drift check, metric | `edge.heartbeat.received.v1` |
| `DispositivoFicouOffline` | read-derived fact | `event-contracts.md §2.1` | audit append, DASH cache hint | `edge.device.offline.v1` (**first to move**) |
| `DispositivoVoltouOnline` | read-derived fact | `event-contracts.md §2.1` | audit append, DASH cache hint | `edge.device.online.v1` |
| `ProfileDriftDetected` | domain (in-process) | `event-contracts.md §2.1` | log/metric, DASH cache hint | `edge.profile.drift.v1` |

**Consumed events:** none. EDGE consumes no events (no broker); cross-service inputs arrive as synchronous
REST calls (heartbeat from the edge agent; GEST profile pointer fetched outbound).

**Notes:**
- `DispositivoFicouOffline` is **derived at read time** (ADR-006), not emitted by a scheduler — it is
  materialized when observed by a DASH poll or the next heartbeat (event-contracts §3).
- When a broker is later enabled, `DispositivoFicouOffline` is the first event to move onto an async topic
  with no rename — the platform's proactive "monitoring dropped" alert.
