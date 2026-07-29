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
- **Language/runtime:** Java 21
- **Framework:** Spring Boot 3.5.x (project standard)
- **Persistence:** PostgreSQL 15+, schema-per-empresa (multi-tenant, ADR-003 shared)
- **Messaging:** none — `stack.messaging.enabled = false`. Inter-module calls are synchronous REST.
- **Service discovery / edge:** Eureka (REG) for in-monorepo discovery; Spring Cloud Gateway (GWY)
  as the single ingress, including for edge profile pulls.
- **Migrations:** centralized Flyway via the MIG service (no local Flyway in gest-service).
- **No TechLab AI, no search engine** — `ai.hasAi = false`; low event volume → direct SQL suffices.

## Service Boundary
`gest-service` is its own independent deployable — one of the N services in the platform
(ADR-002 shared establishes independent microservices as the composition stance). The decisive
boundary *within* this service's own scope is not further backend decomposition but
**cloud-vs-edge**: the CV `GestureDetectionEngine` is a *separate deployable*, entirely outside the
Java service boundary (ADR-005 local). gest-service is the cloud-side detection authority; the edge
is a REST client. No further splitting of gest-service itself is warranted at MVP volume.

## Package Organization
```
com.neuroassist.gest
├── api            # REST controllers, DTOs, OpenAPI (catalog, lifecycle, profile, contract)
├── application    # use cases / ports (register primitive, transition state, build/publish profile,
│                  #   validate emergency eligibility, resolve profile for device)
├── domain         # TipoGesto aggregate + DetectabilityState machine; DetectionProfile aggregate;
│                  #   invariants; domain events (in-process)
└── infrastructure # JPA repositories (schema-per-tenant), profile serializer, outbound port to EDGE
```

## Hub Service Dependencies
- **GWY (API Gateway):** ingress + auth propagation for both caregiver/conductor traffic and edge
  profile pulls.
- **REG (Eureka):** service registration/discovery within the monorepo.
- **MIG (Migrations):** owns all Flyway schema evolution, including gest-service tables.
- **No TechLab AI hub dependency.**

## External Integrations
- **Edge `GestureDetectionEngine` (in-product, out-of-process):** consumes the detection profile
  (pull) and the detection contract (report). Contract is the OpenAPI published by `api`. This is the
  only integration that crosses the process boundary and it is explicitly one-directional for pixels
  (nothing visual comes in).
- **MediaPipe / landmark library:** dependency of the edge agent only — never a gest-service dependency.

## Async vs Sync Decisions
- **Synchronous REST for everything in the MVP.** Peer services (PAC/EVNT/EDGE/DASH) call gest-service
  over REST/synchronously (routed via GWY, discovered via REG); the edge pulls profiles synchronously
  over HTTPS.
- **Pull, not push, for profiles.** The edge pulls its profile (aligned with EDGE heartbeat), which
  keeps gest-service decoupled from edge availability and avoids needing a broker or callbacks.
- **Broker deferred (Framing §11.2).** If patient/edge fan-out grows, `DetectionProfilePublished`
  and recognized-command reports are the natural first candidates to move onto an async bus — the
  in-process domain events in doc 03 are named to make that extraction cheap. Not in MVP scope.
