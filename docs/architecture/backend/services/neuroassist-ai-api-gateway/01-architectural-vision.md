<!-- OUTPUT-SCHEMA
- Intent
- Problem Statement
- Personas
- MVP Scope
- Out of Scope
- Architectural Principles
- Success Signals
-->

# 01 — Architectural Vision

## Intent
The **API Gateway (GWY)** is the single, secure ingress of the NeuroAssist AI backend.
It exists so that every caller — the Angular caregiver dashboard and the patient-side
edge agents — reaches the independently deployed backend services through one stable,
authenticated boundary. It authenticates the caller, resolves the target service through
the Service Registry, extracts and propagates the `empresa` (tenant) context, applies
edge protections (rate limiting, CORS), and shields internal services from the public
network. It is an **infrastructure** service: it carries no business domain and owns no
persistent business state.

## Problem Statement
NeuroAssist runs as a set of discovery-registered backend services (EVNT, PAC, ACS,
HIST, DASH, EDGE) plus infrastructure services (REG, MIG). Two very different client
classes need to reach them:

- **Edge agents** push structured help events at high frequency over REST/HTTPS with a
  local retry queue; they carry a device credential, not an interactive session.
- **The caregiver dashboard** is an interactive SPA carrying a caregiver JWT.

Without a gateway, each service would independently re-implement authentication, tenant
extraction, CORS, rate limiting and network exposure — inconsistently and with a wider
attack surface. Internal service topology (host/port, instance count) would leak to
clients, and cross-cutting policy could not be enforced in one place. The gateway
concentrates ingress concerns so downstream services stay focused on their domain and
trust a pre-resolved, tenant-scoped request context.

## Personas
- **Edge agent (device) — primary.** Headless client; authenticates with a
  device credential provisioned via EDGE pairing; pushes structured events with retry.
  Cares about: low-latency accept, deterministic auth errors, back-pressure signalling
  (429) so the outbox can pace itself.
- **Caregiver dashboard (Angular SPA) — primary.** Interactive session bearing a
  caregiver JWT issued by ACS; browses history, calibrates commands, receives
  notifications. Cares about: correct CORS, transparent token expiry (401), fast routing.
- **Platform operator / SRE — secondary.** Operates the platform; needs routing-health
  visibility, correlation-id continuity across services, and clear rate-limit and
  upstream-failure signals for triage.

## MVP Scope
1. **Dynamic routing** to backend services via Service Registry (Eureka) discovery —
   `lb://<service>` routes, no hard-coded hosts.
2. **Authentication at the edge:** validate caregiver JWTs (against ACS's JWK set) and
   validate edge device credentials; reject unauthenticated traffic before it reaches a
   service.
3. **Tenant context propagation:** extract `empresa_id` from the validated principal and
   forward it downstream as `X-Empresa-Id` (the contract every service trusts).
4. **Correlation-id propagation:** honour an inbound `X-Correlation-Id`, generate one if
   absent, and forward it on every routed request for end-to-end tracing.
5. **Per-route rate limiting** protecting the edge-ingest and login routes.
6. **CORS policy** for the dashboard origin.
7. **Typed error shaping:** gateway-originated errors returned as RFC 7807
   `application/problem+json` (401/403/429/502/503/504), consistent with the project
   error model.

## Out of Scope
- Asynchronous messaging / broker fan-out — `stack.messaging.enabled = false`.
- Response caching, request/response body transformation, GraphQL federation.
- API monetization, quota billing, developer portal.
- Full WAF / bot mitigation (delegated to the infra/network edge).
- mTLS service mesh between internal services (future hardening).
- Business logic of any routed domain — the gateway never interprets payloads.

## Architectural Principles
- **Thin and stateless.** The gateway holds no business state; any state (rate-limit
  counters) is externalised or in-memory-ephemeral. It can scale horizontally freely.
- **Fail closed on identity.** Unauthenticated or unresolvable-tenant requests are
  rejected at the edge, never forwarded "just in case".
- **Trust boundary made explicit.** Downstream services trust `X-Empresa-Id` /
  authenticated principal *because* the gateway is the only ingress; this contract is
  documented and enforced (see ADR-005).
- **Discovery, not addresses.** Routes target logical service ids resolved via Eureka;
  topology changes never require gateway redeploys.
- **Observable by default.** Every routed request carries a correlation id and emits a
  structured access line; rejections are typed and countable.
- **Policy in one place.** CORS, rate limiting and auth live at the gateway so downstream
  services do not re-implement them.

## Success Signals
- 100% of external traffic transits the gateway; no service is reachable directly from
  the public network.
- Every downstream request carries a valid `X-Empresa-Id` and `X-Correlation-Id`
  (verifiable in downstream logs).
- Unauthenticated / unauthorized traffic is rejected at the edge with the correct typed
  status (401/403) and never reaches a domain service.
- Edge-ingest and login routes are rate-limited; back-pressure surfaces as 429 and the
  edge outbox paces correctly without data loss.
- Adding or scaling a backend service requires no gateway code change — only a registry
  registration.
