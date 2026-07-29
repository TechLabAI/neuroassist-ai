<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos
- **R-1 — Single point of failure at the ingress.** All external traffic transits the
  gateway; an outage takes the whole product offline. Mitigation: stateless design + N
  replicas behind an external LB; health checks; fast restart.
- **R-2 — Tenant spoofing via headers.** If inbound `X-Empresa-Id` (or other internal
  headers) is not stripped, a caller could impersonate another `empresa`. Mitigation:
  INV-2 — always derive tenant from the principal and strip client-supplied values
  (enforced in `TenantContextPropagationFilter`; must be covered by a security test).
- **R-3 — In-memory rate limiting is per-replica.** With N replicas the effective limit is
  N× the configured value, weakening back-pressure for the edge outbox. Mitigation:
  document the behaviour; move to a shared store (Redis) if precise global limits become
  necessary (see ADR-005 / assumptions).
- **R-4 — JWK/issuer availability coupling.** If ACS's JWK endpoint is unreachable and the
  cache is cold/expired, caregiver auth fails. Mitigation: cache with generous TTL +
  stale-while-revalidate; alert on refresh failure.
- **R-5 — Reactive/servlet skill split.** The gateway is the only WebFlux service; the
  team must handle non-blocking idioms correctly (no blocking calls in filters).
- **R-6 — LGPD in logs.** Access logs must not capture payload bodies or PII; only
  ids/route/status. Risk of accidental sensitive-data logging.

## Gaps Conhecidos
- **G-1 — Edge device-credential scheme undefined.** The exact credential (signed device
  token vs mTLS client cert vs API key) is not yet decided; it depends on EDGE pairing
  design. Captured as the driver for ADR-005 and must be resolved before Specs finalise
  the auth contract.
- **G-2 — Coarse vs fine authorization split.** Which authz decisions live at the gateway
  (coarse, route-level) vs inside each service (fine, resource-level) needs an explicit
  contract per route.
- **G-3 — Rate-limit policy values.** Concrete limits/replenish rates per route (ingest,
  login) are not yet quantified — need edge-volume estimates from EDGE/EVNT.
- **G-4 — TLS termination point.** Whether TLS terminates at the external LB or at the
  gateway per environment is an infra decision still open.

## Suposicoes a Validar
- **A-1:** ACS exposes a standard JWK set / OIDC discovery endpoint the gateway can trust
  for caregiver JWT validation.
- **A-2:** A single-region, in-memory rate limiter is acceptable for the MVP (no strict
  global counter required yet).
- **A-3:** All backend services register with Eureka and are reachable exclusively via
  discovery (no direct-address routes needed).
- **A-4:** No asynchronous broker is required at the ingress for the MVP; the edge outbox
  + 429 back-pressure is sufficient (`stack.messaging.enabled = false`).
- **A-5:** The gateway is the *only* public ingress — no service is exposed directly.

## Dependencias Criticas
- **Service Registry (REG / Eureka):** hard runtime dependency — no discovery, no routing.
- **Cuidadores e Acesso (ACS):** JWK/issuer for caregiver auth and the device-credential
  authority (with EDGE) — hard dependency for authentication.
- **External load balancer / DNS (infra):** fronts gateway replicas; required for HA.
- **Error model (`arch/decisions/error-model.arch.json`):** the gateway must emit RFC 7807
  problems consistent with the project contract.
- **Downstream services (EVNT/PAC/ACS/HIST/DASH/EDGE):** must honour `X-Empresa-Id` and
  `X-Correlation-Id` for the trust/observability contract to hold end to end.
