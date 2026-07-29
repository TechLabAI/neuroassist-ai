<!-- OUTPUT-SCHEMA
- Riscos
- Gaps Conhecidos
- Suposicoes a Validar
- Dependencias Criticas
-->

# 06 — Risks, Gaps, Assumptions

## Riscos
- **Single point of failure (MVP):** a single-node registry means REG downtime blocks
  new registrations and fresh discovery. Mitigated by client-side registry caching
  and fast restart; residual risk accepted for pilot scale (see local ADR-002).
- **Self-preservation false positives:** in a very small deployment, a couple of
  missed heartbeats can trip self-preservation and keep dead instances listed,
  routing traffic to nothing. Requires careful lease/threshold tuning.
- **Unauthenticated dashboard:** if the internal network boundary leaks, the Eureka
  dashboard exposes the service topology. Network isolation is the only MVP control.
- **Version drift:** an incompatible Spring Cloud ↔ Spring Boot pairing breaks the
  Eureka server; the release-train pin must be enforced at scaffold time.

## Gaps Conhecidos
- No HA/peer replication in MVP — deferred until instance count justifies it.
- No authentication/authorization on dashboard or actuator beyond network isolation.
- No formal SLO for registry availability yet; operators rely on the dashboard.
- Lease/renewal and self-preservation thresholds not yet tuned to real pilot load.

## Suposicoes a Validar
- Pilot instance count is low enough that a single Eureka node suffices.
- The internal network is a trustworthy boundary for the MVP (no zero-trust yet).
- All business services and the gateway adopt the standard Spring Cloud discovery
  client and register under stable logical names.
- Clients tolerate a brief REG restart via cached registry snapshots without manual
  intervention.

## Dependencias Criticas
- **Spring Cloud Netflix Eureka Server** compatible with Spring Boot 3.5.x (pinned
  release train).
- **JVM / Java 21** runtime and the backend Gradle monorepo root.
- **Reachable internal network** so clients can hit REG's `defaultZone`.
- REG must be **first to become healthy** in the boot order so `api-gateway` and
  business services can register on startup.
