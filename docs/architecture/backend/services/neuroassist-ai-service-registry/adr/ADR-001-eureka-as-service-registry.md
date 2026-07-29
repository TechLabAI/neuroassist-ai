<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-001 — Netflix Eureka as the Service Registry

## Status
aceita

## Contexto
NeuroAssist AI runs several independent Spring Boot backend services behind an API
Gateway. They must discover each other by logical name to survive restarts and
relocation. Candidate approaches: Netflix Eureka (Spring Cloud Netflix), HashiCorp
Consul, Kubernetes-native service discovery, or static configuration. The backend
already standardizes on the Spring ecosystem (Java 21, Spring Boot 3.5.x, Spring
Cloud Gateway), the pilot runs at small scale (home/family), and the team wants the
least operational overhead.

## Decisão
We will use **Netflix Eureka Server** (`spring-cloud-starter-netflix-eureka-server`)
as the service registry, because it integrates natively with Spring Cloud Gateway
and the Spring Cloud discovery client, requires no external infrastructure (no
Consul agent, no Kubernetes dependency), and is configuration-driven — matching the
"boring infrastructure, config not code" principle. Static configuration is rejected
as too brittle; Consul/Kubernetes discovery are rejected as over-engineered for the
pilot's scale and stack.

## Consequências
- Positivas: seamless Spring Cloud integration; zero extra infra to operate; a
  built-in dashboard for operators; well-documented defaults; easy client onboarding.
- Negativas: couples the platform to the Spring Cloud Netflix release train
  (version-pin discipline required); Eureka's AP/eventual-consistency model means
  brief windows of stale registry data; ties discovery to the JVM ecosystem.
- Trade-offs aceitos: we accept eventual consistency and single-vendor coupling in
  exchange for minimal operational cost and native fit with the existing stack.
