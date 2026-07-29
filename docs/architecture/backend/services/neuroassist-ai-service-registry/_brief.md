# _brief — REG (Service Registry)

> Structured brief captured for `/be-architect-vision neuroassist-ai REG`.
> Source of truth for the following architect phases (specs, backlog, scaffold).
> REG is an **infrastructure module** (`isInfrastructure: true`, `status: declared`)
> — a platform capability, not a business domain.

## 1. Canonical name and prefix
- **Prefix:** `REG`
- **Canonical name:** `service-registry`
- **Backend service:** `neuroassist-ai-service-registry`
- **Package base:** `com.neuroassist`

## 2. Problem the tool solves
Backend services (`api-gateway`, `migrations`, and every future business service:
EVNT, PAC, ACS, HIST, DASH, EDGE) need to discover each other by logical name
instead of hardcoded host:port. The Service Registry provides a single source of
truth for live service instances so the platform can scale, restart and relocate
services without reconfiguration.

## 3. Personas
- **Primary:** Platform / DevOps operator — deploys and observes the backend,
  needs a reliable place to see which services are up.
- **Secondary:** Every other backend service (machine persona) — registers itself
  on startup and resolves peers at runtime; the API Gateway is the heaviest client.

## 4. MVP scope (minimum viable)
- Standalone Netflix Eureka server (Spring Cloud Netflix Eureka Server).
- Single-node registry for the pilot (family/home deployment, low instance count).
- Registration, lease renewal (heartbeat) and eviction of client services.
- Eureka dashboard exposed on the internal network for operators.
- Actuator health endpoint for liveness/readiness.

## 5. Out of scope (MVP)
- Multi-node / peer-aware HA cluster (single instance is sufficient for pilot load).
- Persistent backing store (registry stays in-memory by design).
- Client-side custom discovery logic (standard Spring Cloud discovery client only).
- Business data, multi-tenancy schemas, LGPD-scoped data (none flows through REG).
- Authentication of the dashboard beyond network isolation (revisit before production).

## 6. Mandatory external integrations
- None external. REG is inward-facing platform infrastructure.
- Internal clients: `api-gateway` (routing + discovery), and all business services
  register as Eureka clients.

## 7. TechLab AI / event dependencies
- **TechLab AI services:** none (`ai.hasAi = false`).
- **Message broker:** none (`stack.messaging.enabled = false`). All discovery is
  synchronous HTTP (Eureka REST protocol); REG publishes no domain events.

## 8. Domain anchor event
- **"Instância registrada" (Instance registered)** — a service instance joins the
  registry via lease creation. Companion operational transitions:
  "Lease renovado" (heartbeat) and "Instância despejada" (lease expired → evicted).
