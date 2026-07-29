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
- **Framework:** Spring Boot 3.5.x with Spring Cloud Netflix Eureka Server
  (`spring-cloud-starter-netflix-eureka-server`).
- **Build:** Gradle (Kotlin DSL), aligned with the backend monorepo root.
- **Observability:** Spring Boot Actuator (`/health`, `/info`).
- **Persistence:** none — registry is in-memory.
- **Messaging:** none (`stack.messaging.enabled = false`).

Spring Cloud release train must be pinned to a version compatible with Spring Boot
3.5.x; this pin is a shared platform decision confirmed at scaffold time.

## Service Boundary
REG is a **single, standalone service** — see local **ADR-001** (Eureka as the
service registry) and **ADR-002** (standalone vs HA cluster). It is one of three
infrastructure services (`api-gateway`, `service-registry`, `migrations`) that
underpin the business services. No decomposition: a registry is atomic by nature.

## Package Organization
REG does **not** follow the hexagonal `api/application/domain/infrastructure`
layout — there is no domain. The skeleton is deliberately minimal:
```
com.neuroassist.serviceregistry
  ServiceRegistryApplication   // @SpringBootApplication @EnableEurekaServer
```
Behaviour is configured, not coded, in `application.yml`. Any deviation from this
"config-only" shape must be justified by a new ADR.

## Hub Service Dependencies
- **Depends on:** nothing. REG is the root of the discovery graph and must be
  reachable before other services complete registration.
- **Depended on by:** `api-gateway` (discovery + routing), and every business
  service (EVNT, PAC, ACS, HIST, DASH, EDGE) plus `migrations` as Eureka clients.
- **TechLab AI:** no dependency (`ai.hasAi = false`).

## External Integrations
None. REG integrates only with internal Eureka clients over the Eureka REST
protocol. It exposes no public API and calls no third party.

## Async vs Sync Decisions
- **Synchronous only.** Discovery uses Eureka's HTTP REST protocol (register /
  renew / cancel / fetch registry). Consistent with the project-wide decision to
  avoid a message broker in the MVP (edge→backend sync REST with local outbox retry).
- **No async flows** originate from or terminate at REG. If a broker is later
  introduced for business events, REG remains synchronous and unaffected.
