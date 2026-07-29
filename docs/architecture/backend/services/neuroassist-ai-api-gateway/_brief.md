# GWY — Structured Brief (API Gateway)

> Source for the following commands (`/be-architect-specs`, `-backlog`, `-scaffold`).
> Module is **infrastructure** (`isInfrastructure: true`, `hasUi: false`) — the single
> secure entry point of the NeuroAssist AI backend, not a business domain service.

## 1. Canonical name and prefix
- Canonical name: `api-gateway`
- Prefix: `GWY`
- Backend service: `neuroassist-ai-api-gateway`
- Technology: Spring Cloud Gateway (reactive, WebFlux)

## 2. Problem the tool solves (1–2 sentences)
NeuroAssist has two distinct client classes — the Angular caregiver dashboard and the
edge agents running on patients' devices — that must reach a set of independently
deployed backend services. The gateway gives both a single stable ingress that
authenticates the caller, resolves the target service via discovery, extracts and
propagates the `empresa` (tenant) context, and shields internal services from the
public network.

## 3. Primary and secondary personas
- **Primary A — Edge agent (device):** a headless client that authenticates with a
  device credential and pushes structured help events over REST/HTTPS with a local
  retry queue (outbox). High call frequency, no interactive session.
- **Primary B — Caregiver dashboard (Angular SPA):** an interactive user session
  bearing a caregiver JWT; browses history, calibrates sensitivity, receives
  notifications.
- **Secondary — Platform operator / SRE:** observes routing health, latency, rate-limit
  rejections and correlation traces across services.

## 4. MVP scope (minimum viable)
- Reverse proxy / dynamic routing to backend services via Service Registry (Eureka) discovery.
- Authentication at the edge: JWT validation (caregiver sessions) and device-credential
  validation (edge agents) — reject unauthenticated traffic before it reaches services.
- Tenant (`empresa`) context extraction from the validated token and propagation to
  downstream services via the `X-Empresa-Id` header.
- Correlation-id propagation (`X-Correlation-Id`): generate if absent, forward always.
- Per-route rate limiting (protect edge-ingest and login routes).
- CORS policy for the dashboard origin.
- RFC 7807 (`application/problem+json`) error passthrough / gateway-level error shaping
  for 401/403/429/502/503/504.

## 5. Out of scope (MVP)
- Asynchronous messaging / broker fan-out (`stack.messaging.enabled = false`).
- Response caching, request/response body transformation, GraphQL federation.
- API monetization, quota billing, developer portal.
- Full WAF / bot mitigation (delegated to infra/edge network layer).
- Service-mesh sidecar concerns (mTLS between internal services) — future.

## 6. Mandatory external integrations
- **Service Registry (REG / Eureka):** source of routable service instances.
- **Cuidadores e Acesso (ACS):** issuer/authority of caregiver identity and JWT; the
  gateway validates tokens against ACS's JWK set.
- **All routed backend services:** EVNT, PAC, ACS, HIST, DASH, EDGE.
- **Edge device authentication authority:** device credentials are provisioned via EDGE
  (device pairing) and validated at the gateway.

## 7. TechLab AI / cross-module dependencies
- TechLab AI: **none** (`ai.hasAi = false`).
- Message broker: **disabled** — all downstream calls are synchronous REST over discovery.
  No event-driven coupling from the gateway.

## 8. Domain anchor event
- "Requisição roteada" (**Request routed**) — a caller was authenticated, tenant context
  resolved, and the request forwarded to a discovered service instance (or rejected with
  a typed error). The gateway has no business aggregate; its "event" is the routing
  decision itself, surfaced as a structured access/audit log line.
