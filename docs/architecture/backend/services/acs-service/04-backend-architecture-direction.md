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
- **Language / runtime:** Java 21.
- **Framework:** Spring Boot 3.5.x (Web, Validation, Data JPA, Security).
- **Security:** Spring Security — ACS acts as the platform's **OAuth2 authorization server** (issues
  JWT, exposes a JWK set); peers are OAuth2 **resource servers** validating via `jwk-set-uri` reachable
  through REG (RAG-confirmed convention across GEST/EDGE/EVNT). Password hashing via
  bcrypt/argon2 (`DelegatingPasswordEncoder`).
- **Persistence:** PostgreSQL 15+, schema-per-tenant (`neuroassist_global` + per-empresa schemas). The
  tenant directory and JWK/key material live in `neuroassist_global`; caregiver/linkage rows are
  tenant-scoped.
- **Migrations:** centralized Flyway via the `neuroassist-ai-migrations` (MIG) service — ACS does not
  run its own migrator and additionally *requests* tenant-schema provisioning from MIG (see doc 05).
- **Service discovery / routing:** Eureka (REG) + Spring Cloud Gateway (GWY).
- **Messaging:** none — `stack.messaging.enabled = false`; all inter-service communication is
  synchronous REST.
- **AI:** none — `ai.hasAi = false`.

## Service Boundary
`acs-service` is a **single independent microservice** (ADR-002 shared, independent microservices),
deployed on its own, registered in Eureka and routed via GWY. It is the system of record for `Tenant`,
`Cuidador` and the caregiver↔patient linkage, and the token issuer for the whole platform. It is
intentionally *not* decomposed (e.g. a separate "auth-server" and "directory" service): token issuance,
identity and linkage share the same aggregates and tenant context; splitting them would add network
hops and a second key-distribution problem without a bounded-context seam. Note ACS holds a special
role: while all services are peers, ACS is the **trust anchor** every other service depends on for
authentication.

## Package Organization
Hexagonal (ADR-001 shared), package base `com.neuroassist.acs`:
- **`api`** — REST controllers and DTOs for three surfaces: admin (tenants, caregivers, linkages), auth
  (token endpoint, JWK set), directory (read-only resolves). Bean-validation, RFC 7807 error handling,
  `@PreAuthorize` / `@AuditAction`.
- **`application`** — use cases (provision tenant; create/deactivate caregiver; authenticate & issue
  token; link/unlink; directory queries), transaction boundaries, the `Tenant` provisioning state
  machine, and the `role → authorities` policy. Depends on domain + outbound ports only.
- **`domain`** — `Tenant`, `Cuidador`, `CaregiverPatientLink` aggregates; `Papel`, `TenantTipo`,
  `Credential`, `Authority` value objects; invariants and lifecycle. Pure Java, no Spring/JPA imports.
- **`infrastructure`** — JPA entities/repositories (global + tenant-schema resolution), JWT signer /
  key store, password encoder, inbound REST adapters, and **outbound port adapters**:
  `MigProvisioningPort` (request schema creation) and `HistAuditPort` (audit facts), implemented as
  REST clients over Eureka/GWY.

## Hub Service Dependencies
- **TechLab AI:** none. Identity and access are explicit administrative decisions, not inference; ACS
  consumes no orchestration, knowledge, document_composition or analytics capability.

## External Integrations
- **MIG (`neuroassist-ai-migrations`)** — `MigProvisioningPort`: on tenant creation, request the
  per-tenant schema; the tenant becomes `active` only on a successful MIG response. Synchronous REST.
- **HIST (`hist-service`)** — `HistAuditPort`: send audit facts (`CuidadorVinculadoAoPaciente`,
  `CuidadorDesativado`, auth success/failure) synchronously.
- **REG / GWY** — ACS registers in Eureka and publishes its JWK-set endpoint; every other service
  resolves `jwk-set-uri` through REG to validate ACS-issued tokens.
- **Consumers (inbound, ACS is the provider):** PAC `AcsDirectoryPort`, EVNT `CaregiverRoutingClient`,
  EDGE (ACS coordination `EDGE-INT-002`), DASH, HIST — call the directory/auth surfaces synchronously.
- **No external third-party IdP or API** in the MVP.

## Async vs Sync Decisions
- **All cross-service calls are synchronous REST** (messaging disabled).
- **Authentication is stateless / verify-local:** ACS issues a short-TTL JWT; resource servers validate
  locally against the JWK set — no per-request callback to ACS. This is the deliberate scaling seam:
  ACS is not on the hot path of every authorized request, only of sign-in and directory resolves.
- **Tenant provisioning is a synchronous, fail-closed saga against MIG:** the tenant is not `active`
  until MIG confirms the schema; a caregiver cannot be created against a non-active tenant. On
  MIG timeout the tenant stays `provisioning` and the operation is retried — never a half-open tenant.
- **Directory resolves for emergency routing are fail-closed:** EVNT treats an unresolved
  caregiver-for-patient as a routing failure (surface an operational alert) rather than silently
  dropping an emergency.
- **Reevaluation trigger:** if caregiver counts or sign-in volume grow, refresh-token rotation and a
  session-revocation store are the first additions; if the platform later adds a broker, identity
  lifecycle events (deactivation, unlink) are the natural first candidates to publish.
