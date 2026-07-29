# Event Catalog — GWY (API Gateway)

> **No broker events** (`stack.messaging.enabled = false`). The gateway emits **operational
> signals** — structured access-log lines and metrics — not durable messages. This catalog
> lists those signals so observability tooling has a stable contract. See
> `../contracts/event-contracts.md`.

## Broker events

| Name | Type | Payload | Consumers |
|---|---|---|---|
| _(none)_ | — | — | — |

## Operational signals (log / metric)

| Name | Type | Payload (fields) | Known consumers |
|---|---|---|---|
| `request.routed` | access log + counter | correlationId, empresaId, principalKind, route, upstreamStatus, latencyMs | Platform log pipeline; SRE dashboards |
| `request.rejected` | access log + counter | correlationId, empresaId?, route?, status, reason | SRE dashboards; security monitoring |
| `upstream.error` | counter (by route, status) | route, upstreamStatus (502/503/504) | SRE alerting |
| `ratelimit.exceeded` | counter | route, bucket, principalKind | Capacity/back-pressure dashboards; EDGE tuning |
| `auth.failed` | counter | principalKind, reason | Security monitoring |
| `auth.jwk.refresh.failed` | counter + alert | issuer, kid? | On-call alert (R-4) |

## Field notes
- `empresaId` is present on all authenticated flows; absent (or `-`) on pre-auth rejections.
- **LGPD (R-6):** signals carry ids / route / status / latency only — **never** request or
  response bodies, and no PII beyond tenant/principal ids.
- `correlationId` ties every signal to the same value forwarded downstream as
  `X-Correlation-Id`, enabling cross-service trace joins.
