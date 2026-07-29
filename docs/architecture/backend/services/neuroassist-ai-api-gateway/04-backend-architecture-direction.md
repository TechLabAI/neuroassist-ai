<!-- OUTPUT-SCHEMA
- Stack
- Service Boundary
- Package Organization
- Hub Service Dependencies
- External Integrations
- Async vs Sync Decisions
-->

# 04 — Backend Architecture Direction

## Stack
- **Language/runtime:** Java 21.
- **Framework:** Spring Boot 3.5.x with **Spring Cloud Gateway** (reactive, Spring WebFlux
  / Netty). Note: this is the one service in the backend that is reactive by nature; the
  domain services remain servlet-stack Spring MVC.
- **Discovery:** Spring Cloud Netflix Eureka **client** (registers with REG, resolves
  `lb://` routes).
- **Security:** Spring Security (reactive) resource-server style JWT validation against
  ACS's JWK set; a custom filter for edge device-credential validation.
- **Rate limiting:** Spring Cloud Gateway `RequestRateLimiter` (token-bucket). Backing
  store is in-memory for the MVP (single-region); Redis is a documented upgrade path if a
  distributed counter is needed (out of MVP scope, no Redis provisioned today).
- **Config:** externalised route table + policies via `application.yml` / environment.
- **Database:** none. The gateway owns no schema and is not a Flyway target.

## Service Boundary
A single, standalone, stateless service: `neuroassist-ai-api-gateway`. It is the only
public ingress. It does not embed or share code with any domain service. Its "boundary"
is the set of routes it exposes and the filters it applies; it never contains business
logic. Horizontally scalable (N replicas behind an external load balancer / DNS), since
it is stateless apart from ephemeral rate-limit counters. See ADR-002 (local) — single
service, no decomposition.

## Package Organization
Spring Cloud Gateway is filter-oriented rather than domain-hexagonal, but package
discipline mirrors ADR-001:

```
com.neuroassist.gateway
├── config/            # RouteLocator beans, CORS config, rate-limit policies, security config
├── filter/            # global + gateway filters
│   ├── CorrelationIdFilter
│   ├── JwtAuthenticationFilter        # caregiver JWT
│   ├── DeviceCredentialFilter         # edge device auth
│   ├── TenantContextPropagationFilter # sets X-Empresa-Id, strips spoofed inbound
│   └── ProblemDetailErrorFilter       # RFC 7807 shaping
├── security/          # reactive security wiring, JWK/issuer resolution
└── support/           # structured access logging, metrics, header constants
```

There is deliberately **no** `domain/` or `application/service` package — the gateway has
no aggregate and no use case beyond routing.

## Hub Service Dependencies
- **TechLab AI:** none — `ai.hasAi = false`. The gateway makes no orchestration,
  knowledge, document-composition or analytics calls.
- The gateway is infrastructure; it does not depend on any TechLab hub capability.

## External Integrations
- **Service Registry (REG / Eureka):** the gateway registers itself and consumes the
  registry to resolve `lb://<service>` routes. Hard dependency at startup for routing.
- **Cuidadores e Acesso (ACS):** provides the JWK set (`issuer`/`jwks-uri`) for JWT
  validation and, with EDGE, the device-credential authority. The gateway calls ACS's
  well-known endpoints; it does not embed ACS logic.
- **Routed downstream services:** EVNT, PAC, ACS, HIST, DASH, EDGE — reached by discovery,
  not by fixed address. Each trusts the forwarded `X-Empresa-Id` / principal.
- **External load balancer / DNS (infra):** fronts the gateway replicas and terminates
  public DNS; TLS may terminate here or at the gateway per environment.

## Async vs Sync Decisions
- **All downstream calls are synchronous** REST over HTTPS resolved via discovery —
  consistent with `stack.messaging.enabled = false`. There is no broker and the gateway
  publishes/consumes no messages.
- **Edge back-pressure is synchronous-with-retry:** the gateway returns 429 when a route
  bucket is exhausted; the edge agent's local outbox holds and retries with backoff. This
  is the deliberate substitute for asynchronous queuing at the ingress.
- **Reactive internally, synchronous semantically:** the gateway is non-blocking (Netty)
  for throughput, but exposes plain request/response semantics to callers; reactivity is
  an implementation choice, not a contract.
- **Future:** if edge volume grows, an asynchronous broker (e.g. RabbitMQ) may be
  introduced behind an ingest service — the gateway would then route to that service; the
  gateway itself stays synchronous. Tracked as a risk/assumption, not MVP work.
