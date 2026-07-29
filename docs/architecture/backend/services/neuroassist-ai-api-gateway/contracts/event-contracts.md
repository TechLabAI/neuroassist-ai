# Event Contracts — GWY (API Gateway)

> **No message broker.** `stack.messaging.enabled = false` (A-4). The gateway publishes and
> consumes **zero** broker messages. What follows documents that absence and the
> **operational signals** the gateway emits instead (structured log lines / metrics — not
> messages). The full catalog is in `../events/event-catalog.md`.

## 1. Broker events

- **Published:** none.
- **Consumed:** none.
- **Rationale:** all downstream interaction is synchronous REST over discovery
  (`04-backend-architecture-direction.md` §Async vs Sync). Edge back-pressure is expressed
  as `429` + edge outbox retry, not as a queue.

EARS:
- THE SYSTEM SHALL NOT declare, publish to, or consume from any broker
  topic/queue/exchange.

## 2. Operational signals (log lines / metrics, not events)

These are observability records, emitted synchronously to the platform log/metrics
pipeline. They are **not** durable, ordered, or delivery-guaranteed messages.

| Signal | Kind | Fields |
|---|---|---|
| `request.routed` | access log + counter | correlationId, empresaId, principalKind, route, upstreamStatus, latencyMs |
| `request.rejected` | access log + counter | correlationId, empresaId?, route?, status (401/403/404/429), reason |
| `upstream.error` | counter | route, upstreamStatus (502/503/504) |
| `ratelimit.exceeded` | counter | route, bucket, principalKind |
| `auth.failed` | counter | principalKind, reason (expired/invalid/unknown-kid) |

EARS:
- WHEN a request is forwarded, THE SYSTEM SHALL emit a `request.routed` access line
  carrying `correlationId`, `empresaId`, `route`, `upstreamStatus` and `latencyMs`.
- WHEN a request is rejected at the edge, THE SYSTEM SHALL emit a `request.rejected` line
  with the typed status and reason and NO payload body (LGPD, R-6).

## 3. Idempotency & versioning

- Not applicable to broker payloads (there are none). Request-level idempotency is a
  downstream-service concern; the gateway forwards `X-Correlation-Id` so downstream
  services can deduplicate. See SPEC-05.

## 4. Future

If edge volume later justifies an asynchronous ingest path, a broker (e.g. RabbitMQ) would
sit **behind** an ingest service; the gateway would route to it and still publish no
messages itself. Tracked as A-4 / R-? (out of MVP scope).
