<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
REG is a single Spring Boot application acting as a **Netflix Eureka server**. Its
boundary is the registry itself: it owns the in-memory table of service instances,
their leases and lifecycle. It does not own any business data and calls no other
service. Everything inside the boundary is Eureka server behaviour + Spring Boot
actuator; everything outside is an Eureka client or an operator.

## External Actors and Systems
- **Operator (human):** views the Eureka dashboard and actuator health.
- **API Gateway (`neuroassist-ai-api-gateway`):** registers itself and, above all,
  queries the registry to route requests to business services.
- **Business services (EVNT, PAC, ACS, HIST, DASH, EDGE) and `migrations`:** each is
  an Eureka client that registers on startup and renews its lease.
- **No external third-party systems.** REG is inward-facing platform infrastructure.

## End-to-End Flows
**Golden path — instance registration & discovery**
1. A service boots with the Eureka client and its `defaultZone` pointing at REG.
2. The client POSTs a registration (instance id, logical app name, host, port,
   health URL) → REG creates a **lease**.
3. The instance appears in the registry and dashboard; API Gateway can now resolve it.
4. The client renews its lease on a fixed heartbeat interval.
5. API Gateway resolves the logical name → instance list → routes traffic.

**Alternative — graceful shutdown**
- The client sends a cancel/deregister on shutdown → REG removes the lease
  immediately → the instance stops receiving traffic.

**Alternative — ungraceful failure**
- The instance stops renewing → the lease expires after the configured window →
  REG evicts the instance → routing stops sending it traffic.

**Alternative — REG restart**
- REG loses its in-memory table on restart; clients re-register on their next
  heartbeat and steady state is restored without operator action.

## Internal Boundaries
REG intentionally does **not** implement the hexagonal `api/application/domain/
infrastructure` layering used by business services — there is no domain to model.
Its internal structure is minimal:
- **Bootstrap layer:** `@SpringBootApplication` + `@EnableEurekaServer` main class.
- **Configuration layer:** `application.yml` (registry behaviour: standalone mode,
  self-preservation, lease/renewal thresholds, dashboard, actuator exposure).
- **Platform layer (provided by Spring Cloud):** the Eureka server registry engine,
  REST endpoints and dashboard. Not authored here.

## Cross-cutting Concerns
- **Security:** inward-facing only — bind to the internal network, never public.
  Dashboard/actuator protected by network isolation in MVP; harden before production.
- **Observability:** Eureka dashboard as the primary operator view; actuator
  `/health` and `/info` for probes; standard structured logs.
- **Multi-tenancy:** not applicable. REG operates at the platform layer, above and
  outside the `schema-per-empresa` tenancy model; no tenant data passes through it.
- **Resilience:** self-preservation mode governs behaviour under mass heartbeat loss;
  clients cache registry snapshots so a short REG outage does not halt traffic.
- **LGPD/compliance:** no personal data of patients/caregivers ever transits REG;
  it is out of the LGPD data-flow scope.
