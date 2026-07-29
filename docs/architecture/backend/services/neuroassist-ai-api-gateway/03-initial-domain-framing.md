<!-- OUTPUT-SCHEMA
- Ubiquitous Language
- Aggregates and Entities
- Domain Events
- State Transitions
- Invariants
-->

# 03 — Initial Domain Framing

> **Note.** GWY is an **infrastructure** service (`isInfrastructure: true`). It has no
> business domain, no persistent aggregate and no business events. This framing therefore
> models the *routing/ingress* concepts as a technical vocabulary, not a DDD domain. It is
> intentionally light — Specs will formalise route/filter contracts, not entities.

## Ubiquitous Language
- **Route:** a declarative binding of a request predicate (path/method/header) to a
  logical service id (`lb://<service>`) plus an ordered filter set.
- **Principal:** the authenticated caller — either a *caregiver identity* (from a JWT) or
  an *edge device* (from a device credential).
- **Empresa (tenant):** the care unit the principal belongs to; resolved from the
  principal and propagated as `X-Empresa-Id`.
- **Correlation id:** the end-to-end trace token (`X-Correlation-Id`) carried across the
  gateway and every downstream hop.
- **Routing decision:** the outcome of the filter pipeline — *forwarded* to a discovered
  instance, or *rejected* with a typed status.
- **Rate-limit bucket:** the per-route (and per-principal) budget governing back-pressure.
- **Upstream instance:** a concrete backend instance resolved from the Service Registry.

## Aggregates and Entities
None. The gateway owns no business aggregate and persists no domain entity. The only
first-class runtime concepts are transient configuration objects:

- **RouteDefinition** (config, not persisted): predicate + target service id + filters.
- **RateLimitPolicy** (config): route → limit/replenish parameters.
- **GatewayPrincipal** (request-scoped, transient): subject, kind (`caregiver` | `device`),
  `empresa_id`, granted scope.

These are configuration/request-context objects, not domain entities, and carry no
schema in any tenant database.

## Domain Events
No business domain events are produced or consumed (broker disabled). The gateway emits
**operational signals** only, as structured log lines / metrics — not messages:

- **Requisição roteada (Request routed)** — anchor signal: principal authenticated,
  tenant resolved, request forwarded to a discovered instance. Fields: correlationId,
  empresaId, route, principalKind, upstreamStatus, latencyMs.
- **Requisição rejeitada (Request rejected)** — auth/authz/rate-limit/upstream failure,
  with the typed status and reason.

## State Transitions
The gateway is stateless; the only "state machine" is the per-request lifecycle through
the filter pipeline:

```
RECEIVED -> CORS-checked -> correlation-ensured -> authenticated
   -> tenant-resolved -> rate-limit-checked -> routed(FORWARDED)
                                             \-> REJECTED (401|403|429|502|503|504)
```

Each request is independent; no state persists between requests beyond ephemeral
rate-limit counters (externalised or in-memory).

## Invariants
- **INV-1:** A request is forwarded only if it is authenticated (JWT valid or device
  credential valid). Fail closed otherwise.
- **INV-2:** `X-Empresa-Id` forwarded downstream is always derived from the authenticated
  principal; any client-supplied `X-Empresa-Id` is stripped before processing.
- **INV-3:** Every forwarded request carries a non-empty `X-Correlation-Id` (generated if
  the caller omitted it).
- **INV-4:** The gateway never resolves a target by hard-coded host — only via a logical
  service id through discovery.
- **INV-5:** All gateway-originated error responses use `application/problem+json`.
- **INV-6:** The gateway holds no business state; restarting an instance loses nothing but
  in-flight requests.
