<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-002 — Standalone registry (no HA cluster) for the MVP

## Status
aceita

## Contexto
Eureka supports peer-aware clustering for high availability, but each peer adds
deployment, configuration and operational complexity. The NeuroAssist AI pilot is a
small home/family deployment with a handful of service instances and no strict
uptime SLA yet. The registry is in-memory and clients cache the last known registry,
so a short REG outage degrades gracefully rather than halting traffic. This is the
service-level counterpart to the platform decision to keep infrastructure minimal in
the MVP.

## Decisão
We will run **a single standalone Eureka node** (`register-with-eureka: false`,
`fetch-registry: false` on the server) for the MVP, rather than a peer-aware HA
cluster. Clients rely on cached registry snapshots to tolerate brief REG restarts.
HA clustering is deferred and revisited when instance count, tenant count, or an
availability SLA justify it.

## Consequências
- Positivas: minimal deployment and operational cost; simplest possible config; fast
  to stand up and reason about; adequate for pilot scale.
- Negativas: REG is a single point of failure for *new* registrations and fresh
  discovery during downtime; no cross-node replication.
- Trade-offs aceitos: we accept a bounded availability gap during REG restarts,
  mitigated by client-side caching, in exchange for operational simplicity. The path
  to HA is non-breaking (add peers + `defaultZone` entries), so deferring is safe.
