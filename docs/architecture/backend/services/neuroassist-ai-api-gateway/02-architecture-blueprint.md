<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
The gateway is the outermost process of the NeuroAssist backend. Inside its boundary it
owns: route definitions, the authentication/authorization filter chain, tenant and
correlation propagation, rate limiting and CORS. Outside its boundary and explicitly
**not** owned: identity issuance (ACS), service topology (REG/Eureka), and all business
processing (routed services). The gateway terminates public TLS, applies edge policy, and
forwards to internal service instances resolved by discovery. It does not persist
business data and does not interpret domain payloads.

```
        public network                     internal network (discovery)
   +-----------------------+        +----------------------------------------+
   |  Angular dashboard     |        |  EVNT  PAC  ACS  HIST  DASH  EDGE      |
   |  (caregiver JWT)        |        |        ^   (JWK)                       |
   |                         |  HTTPS |        |                              |
   |  Edge agents            | -----> |   +----+-------+   lb:// via Eureka    |
   |  (device credential)    |        |   |  GWY       | ---------------------->|
   +-----------------------+        |   |  gateway    |<-- REG (Eureka)       |
                                      |   +------------+    discovery           |
                                      +----------------------------------------+
```

## External Actors and Systems
- **Edge agent (actor):** device-credential-authenticated client; POSTs structured
  events; expects 2xx / typed 4xx / 429 back-pressure.
- **Caregiver dashboard (actor):** JWT-bearing SPA; CORS-scoped; browses and calibrates.
- **Service Registry — REG / Eureka (system):** supplies the routable instances for each
  `lb://<service>` route. The gateway is a Eureka *client*.
- **Cuidadores e Acesso — ACS (system):** identity authority; exposes the JWK set the
  gateway uses to validate caregiver JWTs, and provisions device credentials (with EDGE).
- **Routed services — EVNT / PAC / ACS / HIST / DASH / EDGE (systems):** trust the
  gateway-supplied `X-Empresa-Id` and authenticated principal.
- **Operator/SRE (actor):** consumes access logs, correlation traces, rate-limit and
  upstream-failure metrics.

## End-to-End Flows
Three representative flows exercise the ingress: a dashboard request (golden path), an
edge event ingest (golden path), and the failure/alternative branches. Every flow runs
through the same ordered filter pipeline (CORS → correlation → auth → tenant → rate-limit
→ route).

### Golden path — dashboard request routed
1. Dashboard sends `GET /api/hist/...` with `Authorization: Bearer <jwt>`.
2. CORS filter admits the origin; correlation filter ensures `X-Correlation-Id`.
3. Auth filter validates the JWT against ACS's JWK set; extracts principal + `empresa_id`.
4. Tenant filter sets `X-Empresa-Id` from the principal (drops any client-supplied value).
5. Route locator resolves `lb://neuroassist-ai-... (HIST)` via Eureka; request forwarded.
6. Upstream response streamed back to the dashboard, correlation id preserved.

### Golden path — edge event ingest
1. Edge agent POSTs a structured event with a device credential.
2. Correlation filter ensures `X-Correlation-Id`; rate-limit filter checks the ingest bucket.
3. Device-credential filter validates the credential; resolves the bound `empresa_id`.
4. `X-Empresa-Id` set; route resolved to EVNT (or EDGE for heartbeat); request forwarded.
5. 2xx returned → edge drains its outbox entry. 429 → edge holds and retries with backoff.

### Alternatives / failure paths
- **Missing/expired token →** 401 `application/problem+json`, no forwarding (dashboard
  redirects to login).
- **Valid token, no permission for route →** 403 (authorization is coarse at the edge;
  fine-grained authz remains in the service).
- **Rate limit exceeded →** 429 with retry hint; edge paces its outbox.
- **No healthy instance in registry →** 503; upstream timeout → 504; upstream 5xx → 502.
  All shaped as typed problems and counted.

## Internal Boundaries
The gateway is Spring Cloud Gateway (reactive WebFlux), so its "hexagon" is a filter
pipeline rather than a domain hexagon, but the layering discipline (ADR-001) still holds:

- **Edge/route layer:** `RouteLocator` definitions (predicate → `lb://<service>` →
  filter set). Declarative; the only place topology intent is expressed.
- **Filter layer (global + per-route):** correlation, authentication (JWT + device),
  tenant propagation, rate limiting, CORS, error shaping. Ordered explicitly.
- **Support/adapters layer:** JWK client to ACS, Eureka discovery client, rate-limit
  store, structured access logger. These are the outbound ports.
- **Config layer:** route table, CORS origins, rate-limit policies, JWK/issuer settings —
  externalised configuration, environment-specific.

No `application`/`domain` business packages exist; there is no aggregate to model.

## Cross-cutting Concerns
- **Security:** TLS termination; JWT validation against ACS JWK; device-credential
  validation; fail-closed on identity; strip inbound `X-Empresa-Id` and internal-only
  headers from external callers to prevent spoofing.
- **Multi-tenancy:** `empresa_id` is derived exclusively from the authenticated principal
  and emitted as `X-Empresa-Id`; the gateway never trusts a caller-supplied tenant value
  (aligned with the schema-per-empresa model, ADR-003 shared).
- **Observability:** `X-Correlation-Id` generated/propagated end-to-end; structured
  access logs (route, tenant, status, latency); rate-limit and upstream-error counters;
  MDC key `empresaId` on gateway-side logs.
- **Resilience:** discovery-driven load balancing; timeouts and typed 502/503/504
  mapping; per-route rate limiting as back-pressure for the edge outbox.
- **Error model:** RFC 7807 `application/problem+json` for all gateway-originated errors,
  consistent with `arch/decisions/error-model.arch.json`.
