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
Provide **NeuroAssist AI** with a single, authoritative Service Registry so backend
services locate each other by logical name rather than static addresses. REG is the
discovery backbone that lets the platform restart, scale and relocate services
without reconfiguring their peers.

## Problem Statement
The backend is a set of independent Spring Boot services (`api-gateway`,
`migrations`, plus business services EVNT, PAC, ACS, HIST, DASH, EDGE). Hardcoding
`host:port` between them is brittle: any restart or relocation breaks routing, and
the API Gateway cannot balance across multiple instances. The platform needs a
runtime source of truth for "which services are up and where", updated as instances
come and go.

## Personas
- **Platform / DevOps operator (primary, human):** deploys the backend, needs a
  dashboard to confirm each service registered and is renewing its lease.
- **Backend service (secondary, machine):** registers itself on startup, renews its
  lease periodically, and resolves peers by logical id. The API Gateway is the
  primary consumer of the registry.

## MVP Scope
- Standalone Netflix Eureka server (`spring-cloud-starter-netflix-eureka-server`).
- Single-node registry sized for pilot load (home/family deployment, few instances).
- Client registration, lease renewal (heartbeat) and eviction on lease expiry.
- Eureka dashboard on the internal network for operator visibility.
- Actuator `/health` for liveness/readiness of the registry itself.

## Out of Scope
- Peer-aware HA cluster (multi-node replication) — deferred until instance count grows.
- Any persistent backing store — the registry is intentionally in-memory.
- Custom discovery logic — standard Spring Cloud discovery client on the clients.
- Business data, multi-tenant schemas, or LGPD-scoped data — none flows through REG.
- Dashboard authentication beyond network isolation — revisit before production.

## Architectural Principles
1. **Boring infrastructure.** Use Spring Cloud Netflix Eureka defaults; add config,
   not code. REG carries no business logic.
2. **Stateless & disposable.** In-memory registry; a restart is recovered by clients
   re-registering. No database, no migrations.
3. **Fail-open for clients.** Discovery outages must degrade gracefully; clients cache
   the last known registry and the platform tolerates a brief REG restart.
4. **Observable.** The dashboard and actuator health are first-class; operators can
   always see registry state.
5. **Least privilege / inward-facing.** REG is never exposed to the public internet;
   only internal services and operators reach it.

## Success Signals
- Every backend service appears in the Eureka dashboard within one heartbeat cycle
  of startup.
- API Gateway routes exclusively via discovered instances (no hardcoded upstreams).
- A killed service instance is evicted and stops receiving traffic within the
  configured lease-expiry window.
- REG restart causes no permanent outage: clients re-register and steady state
  returns automatically.
- Registry adds negligible operational overhead (single lightweight JVM process).
