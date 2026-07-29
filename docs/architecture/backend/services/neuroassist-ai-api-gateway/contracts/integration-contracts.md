# Integration Contracts — GWY (API Gateway)

> External systems and platform dependencies the gateway integrates with, with auth,
> SLA/limits and fallback strategy per integration. (Platform-internal REG/ACS trust
> details are in `hub-integration.md`; this file focuses on the integration mechanics.)

## 1. Service Registry (Eureka) — discovery

| Aspect | Contract |
|---|---|
| Protocol | Eureka client (HTTP registration + fetch registry) |
| Auth | network-internal; no token in MVP |
| SLA | hard dependency; routing impossible without a populated instance cache |
| Fallback | serve from last-known local cache while REG is briefly down; readiness `DOWN` if cache empty; typed `503` to callers |

EARS:
- IF the target service has no healthy registered instance, THEN THE SYSTEM SHALL respond
  `503 /errors/upstream-unavailable`.

## 2. ACS JWK / OIDC — token validation

| Aspect | Contract |
|---|---|
| Protocol | HTTPS GET of JWK set / OIDC discovery |
| Auth | none (public JWK) |
| Rate/SLA | low volume; keys cached with TTL + stale-while-revalidate |
| Fallback | validate against cached keys; on cold cache + endpoint down → caregiver auth fails `401`, alert `auth.jwk.refresh.failed` |

## 3. Edge device authentication authority (EDGE / ACS) — device tokens

| Aspect | Contract |
|---|---|
| Scheme | **proposta** (ADR-005): signed device token issued at EDGE pairing, JWK-validated like caregiver JWTs; carries `device_id` + bound `empresa_id` |
| Status | **not frozen** — depends on gap G-1 (pairing design). Specs keep the device-auth contract behind a pluggable `DeviceCredentialFilter` so the exact scheme can change without touching routing |
| Fallback | invalid/expired device token → `401`; edge outbox holds and retries |

EARS:
- WHERE the device-token scheme (ADR-005) is not yet confirmed, THE SYSTEM SHALL isolate
  device validation in a single filter so the credential scheme is swappable.

## 4. Routed downstream services (EVNT/PAC/ACS/HIST/DASH/EDGE)

| Aspect | Contract |
|---|---|
| Protocol | HTTP(S) over `lb://` discovery |
| Timeouts | connect + response timeout per route; typed `504` on exceed |
| Retry | only idempotent methods (GET) may be retried once on `503`/connect error; never retry non-idempotent (POST) |
| Circuit-breaking | MVP: fail-fast with typed `502/503/504` + counters. Resilience4j circuit breaker is a documented upgrade path (out of MVP) |

EARS:
- WHEN an upstream returns an unhandled `5xx`, THE SYSTEM SHALL map it to `502
  /errors/upstream-error` and NOT leak the upstream body.
- IF a `GET` to an upstream fails with a connect error, THEN THE SYSTEM SHALL retry at most
  once against another discovered instance before responding `503`.

## 5. External load balancer / DNS (infra)

| Aspect | Contract |
|---|---|
| Role | fronts N gateway replicas; public DNS |
| TLS | terminates at LB or at gateway per environment (gap G-4) |
| Health | LB liveness probes `GET /actuator/health` |

EARS:
- THE SYSTEM SHALL answer `/actuator/health` for the external load balancer without
  authentication.
