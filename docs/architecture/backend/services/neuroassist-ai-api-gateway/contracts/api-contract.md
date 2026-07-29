# API Contract — GWY (API Gateway)

> The gateway exposes **no business API of its own**. Its "surface" is (a) the set of
> **routes** it proxies to downstream services and (b) a small set of **management /
> operational endpoints**. All acceptance criteria use EARS notation
> (`../../standards/ears-notation.md`).

## 1. Auth model (edge)

Two principal kinds are authenticated at the edge; both resolve to a `GatewayPrincipal`
(`subject`, `kind ∈ {caregiver, device}`, `empresa_id`, `scope`).

- **Caregiver JWT** — OAuth2/OIDC bearer token issued by **ACS**, validated against ACS's
  JWK set (`jwks-uri` / issuer). Carries `empresa_id` and caregiver scopes.
- **Edge device token** — signed device token issued during EDGE pairing (ADR-005,
  **proposta**), validated the same JWK way; carries `device_id` + bound `empresa_id`.

EARS:
- THE SYSTEM SHALL authenticate every routed request as exactly one principal kind
  (`caregiver` or `device`) before forwarding.
- IF the request bears neither a valid caregiver JWT nor a valid device token, THEN THE
  SYSTEM SHALL reject it with `401` and NOT forward it.
- WHERE a route is annotated `public` (e.g. login, health), THE SYSTEM SHALL forward
  without an authenticated principal.

## 2. Mandatory headers

| Header | Direction | Rule |
|---|---|---|
| `Authorization: Bearer <token>` | inbound | caregiver/device credential; validated, never forwarded verbatim beyond auth need |
| `X-Correlation-Id` | in/out | honoured if present, generated if absent, **always** forwarded downstream and echoed on the response |
| `X-Empresa-Id` | outbound only | set by the gateway from the authenticated principal; **stripped** from any inbound request |
| `X-Principal-Kind` | outbound | `caregiver` \| `device` — informs downstream authz |
| `X-Principal-Id` | outbound | `subject` / `device_id` of the principal |

EARS:
- WHEN a request omits `X-Correlation-Id`, THE SYSTEM SHALL generate a UUIDv4 correlation
  id and forward it downstream.
- THE SYSTEM SHALL set `X-Empresa-Id` on every forwarded request from the authenticated
  principal, never from a client-supplied value.
- IF an inbound request carries `X-Empresa-Id`, `X-Principal-Id` or `X-Principal-Kind`,
  THEN THE SYSTEM SHALL strip those headers before applying its own (anti-spoofing, INV-2).
- THE SYSTEM SHALL echo the effective `X-Correlation-Id` on the response to the caller.

## 3. Route surface (proxied)

Routes target **logical service ids** via discovery (`lb://<service>`), never a host:port
(INV-4). Path prefixes below are the external contract; the stripped prefix is forwarded.

| Route id | Predicate (path) | Target (`lb://`) | Auth | Rate-limit bucket |
|---|---|---|---|---|
| `evnt-ingest` | `POST /api/evnt/**` | `EVNT` (`evnt-service`) | device \| caregiver | `edge-ingest` |
| `evnt-read` | `GET /api/evnt/**` | `EVNT` | caregiver | `default` |
| `pac` | `/api/pac/**` | `PAC` (`pac-service`) | caregiver | `default` |
| `acs-auth` | `/api/acs/auth/**` | `ACS` (`acs-service`) | public | `login` |
| `acs` | `/api/acs/**` | `ACS` | caregiver | `default` |
| `hist` | `GET /api/hist/**` | `HIST` (`hist-service`) | caregiver | `default` |
| `dash` | `/api/dash/**` | `DASH` (`dash-service`) | caregiver | `default` |
| `edge-pair` | `POST /api/edge/pair/**` | `EDGE` (`edge-service`) | public (pairing token) | `login` |
| `edge` | `/api/edge/**` | `EDGE` | device \| caregiver | `edge-ingest` |

EARS:
- WHEN a request matches a route predicate and the principal is authenticated for that
  route, THE SYSTEM SHALL forward it to a discovered instance of the target service.
- IF no route predicate matches the request path, THEN THE SYSTEM SHALL respond `404`
  `application/problem+json` without contacting any service.
- WHILE no healthy instance of the target service is registered, THE SYSTEM SHALL respond
  `503` and count an `upstream_unavailable` signal.

## 4. Management / operational endpoints (gateway-owned)

| Method | Path | Auth | Response |
|---|---|---|---|
| `GET` | `/actuator/health` | public (liveness) | `200 {status:UP}` / `503 {status:DOWN}` |
| `GET` | `/actuator/health/readiness` | internal | `UP` only when registered with Eureka and JWK reachable |
| `GET` | `/actuator/info` | internal | build/version metadata |
| `GET` | `/actuator/prometheus` | internal | metrics scrape (rate-limit, upstream, auth counters) |
| `GET` | `/actuator/gateway/routes` | internal | effective route table (debug) |

EARS:
- WHILE the gateway is not registered with the Service Registry OR the ACS JWK set is
  unreachable and the cache is cold, THE SYSTEM SHALL report readiness `DOWN`.
- THE SYSTEM SHALL expose `/actuator/health` without authentication for load-balancer
  liveness checks.

## 5. Error model (RFC 7807 · `application/problem+json`)

All gateway-originated errors use `application/problem+json` (INV-5), consistent with the
project error model. Body: `{ type, title, status, detail, instance, correlationId }`.

| Status | `type` (suffix) | When |
|---|---|---|
| `401` | `/errors/unauthenticated` | missing/expired/invalid token |
| `403` | `/errors/forbidden` | valid principal, route not permitted (coarse authz) |
| `404` | `/errors/no-route` | no route predicate matched |
| `429` | `/errors/rate-limited` | route bucket exhausted; includes `Retry-After` |
| `502` | `/errors/upstream-error` | upstream returned an unhandled 5xx |
| `503` | `/errors/upstream-unavailable` | no healthy instance / gateway not ready |
| `504` | `/errors/upstream-timeout` | upstream exceeded the response timeout |

EARS:
- IF a route's rate-limit bucket is exhausted, THEN THE SYSTEM SHALL respond `429` with a
  `Retry-After` header and a `/errors/rate-limited` problem body.
- WHEN an upstream call times out, THE SYSTEM SHALL respond `504` and NOT retry a
  non-idempotent method automatically.
- THE SYSTEM SHALL include the effective `correlationId` in every problem body.

## 6. CORS

- THE SYSTEM SHALL admit cross-origin requests only from the configured dashboard
  origin(s) (`gateway.cors.allowed-origins`).
- WHERE a request is a CORS pre-flight (`OPTIONS`), THE SYSTEM SHALL answer it at the
  gateway without forwarding to any service.
- IF an origin is not in the allow-list, THEN THE SYSTEM SHALL omit the
  `Access-Control-Allow-Origin` header (browser blocks the response).

## 7. Filter order (normative)

`CORS → CorrelationId → Authentication (JWT | Device) → TenantContextPropagation
(strip+set) → RateLimiter → Route(lb://) → ProblemDetailError (wrap)`.
See SPEC-02 for the pipeline contract.
