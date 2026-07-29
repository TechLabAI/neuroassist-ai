<!-- OUTPUT-SCHEMA
- Status
- Contexto
- Decisão
- Consequências
-->

# ADR-002 — Single-service gateway (no decomposition)

## Status
aceita

## Contexto
The gateway must front a set of discovery-registered backend services for two client
classes (caregiver dashboard, edge agents). We must decide whether the ingress is a
single deployable or is decomposed (e.g. a separate edge-ingest gateway vs a dashboard
gateway, or splitting auth into its own edge service). Forces: the ingress is stateless
and cross-cutting (auth, tenant propagation, routing, rate limiting apply to both client
classes); the team is small; operational simplicity and a single trust boundary are
valued; `stack.messaging.enabled = false` so there is no async fan-out to justify extra
moving parts. Route- and client-specific behaviour can be expressed as per-route filter
configuration inside one service.

## Decisão
We will implement GWY as a **single, standalone Spring Cloud Gateway service**
(`neuroassist-ai-api-gateway`), not decomposed. Client-class differences (JWT vs device
credential, different rate-limit buckets, CORS) are handled by **per-route filter
configuration** within the one service. Horizontal scale is achieved by running N
identical stateless replicas behind an external load balancer, not by splitting the
gateway into multiple services.

## Consequências
**Positivas**
- One trust boundary and one place to enforce auth, tenant propagation and rate limiting.
- Minimal operational surface; simple local development and deployment.
- Stateless → trivially horizontally scalable.

**Negativas / trade-offs aceitos**
- All ingress traffic shares one process class → a single point of failure (mitigated by
  replicas; see 06 R-1).
- Edge and dashboard traffic are not isolated at the process level; a misbehaving client
  class could pressure shared capacity (mitigated by per-route rate limiting and, if
  needed later, separate replica pools of the same image).
- If edge and dashboard ingress diverge sharply (very different scaling/latency SLOs), a
  future split may be reconsidered — reversible, since routes/filters are declarative.
