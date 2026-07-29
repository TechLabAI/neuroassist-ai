# REG — Event Catalog

> **Empty by design.** REG is not a message-broker participant — it publishes and
> consumes no domain events (see `contracts/event-contracts.md`). No broker is even
> present on the platform (`stack.messaging.enabled = false`).

| Event | Type | Payload | Known consumers |
|---|---|---|---|
| _(none)_ | — | — | — |

## Note on lifecycle signals

REG emits **registration / renewal / eviction lifecycle logs** and access logs to
the platform observability pipeline, plus Actuator metrics. These are log/telemetry
records, **not** broker domain events, and are described in
`spec/backend/SPEC-07-observability-health.md`, not here.
