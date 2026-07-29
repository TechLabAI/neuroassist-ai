<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
`acs-service` is a single Spring Boot microservice owning identity, access and tenant provisioning. It
holds two data surfaces: the **global tenant/identity directory** in `neuroassist_global` (tenants,
caregiver accounts, linkages) and the **authentication authority** that mints JWTs and exposes a JWK
set. Its API has three surfaces: an **admin surface** (create tenants, caregivers, linkages — used by
DASH/onboarding through GWY), an **auth surface** (login → token, JWK set — used by clients and, for
JWK, by every service), and a **directory surface** (read-only resolves — used service-to-service by
PAC/EVNT/EDGE/DASH/HIST). ACS is inside the trust boundary as the *issuer*; every other service is a
*resource server* relative to it.

## External Actors and Systems
- **Caregivers (Jamilly / clinic coordinator)** — human users authenticating and, for coordinators,
  administering accounts and linkages (via DASH → GWY).
- **GWY (Spring Cloud Gateway)** — routes external traffic to ACS; forwards the bearer token.
- **REG (Eureka)** — service discovery; how peers locate ACS and the JWK endpoint (`jwk-set-uri` via
  REG).
- **MIG (`neuroassist-ai-migrations`)** — centralized Flyway; provisions the per-tenant schema on ACS's
  request.
- **Consumer services (resource servers):** PAC (`AcsDirectoryPort`), EVNT (`CaregiverRoutingClient`),
  EDGE (ACS coordination, `EDGE-INT-002`), DASH, HIST — all call ACS synchronously and all validate
  ACS-issued JWTs via the published JWK set.
- **No third-party IdP / external API in the MVP.**

## End-to-End Flows
The critical flows all pass through ACS as issuer or provisioner: sign-in, tenant/caregiver
provisioning, and caregiver-to-patient assignment. All calls are synchronous REST (messaging disabled).

### Golden path — caregiver signs in and receives scoped access
1. Caregiver submits credentials to the ACS auth surface (via GWY).
2. ACS verifies the credential hash, loads the `Cuidador`, its `Tenant` and its `papel`.
3. ACS mints a signed JWT: `sub = cuidadorId`, `tenantId`, `authorities = roleToAuthorities(papel)`.
4. Client calls other services with the bearer token; each service validates it locally against ACS's
   JWK set and enforces `@PreAuthorize` — no callback to ACS.
5. When a service needs scope data (e.g. EVNT deciding who to alert), it calls the ACS **directory
   surface** to resolve the patient's responsible caregiver(s).

### Flow — tenant + caregiver provisioning (onboarding)
1. Technical conductor / coordinator requests a new tenant (`tipo`).
2. ACS creates the `Tenant` row in `neuroassist_global` (status `provisioning`).
3. ACS requests the per-tenant schema from **MIG**; on success marks the tenant `active` (ADR-004).
4. ACS creates the first `Cuidador` (role, credentials) bound to that tenant.
5. Downstream services can now create tenant-scoped data (PAC patient, EDGE device, ...).

### Flow — assign caregiver to patient (anchor event)
1. Coordinator (or, in a family tenant, the conductor) links a `Cuidador` to a `Paciente` id.
2. ACS validates both belong to the same tenant, writes the linkage → **"Cuidador vinculado ao
   paciente"**.
3. ACS sends the audit fact to HIST synchronously; EVNT/EDGE/DASH now resolve routing from this linkage.

### Alternative / unwanted paths
- **Invalid credentials / unverifiable token** → 401; fail closed, no partial access.
- **Cross-tenant linkage attempt** (caregiver and patient in different tenants) → 422, rejected.
- **MIG provisioning fails/timeouts** → tenant stays `provisioning`, not `active`; no caregiver can be
  created against a non-active tenant (compensating retry, not a half-open tenant).
- **Directory lookup for an unknown patient/tenant** → 404; callers (EVNT) treat routing resolution as
  fail-closed for emergencies.

## Internal Boundaries
Hexagonal (ADR-001 shared), package base `com.neuroassist.acs`:
- **`api`** — REST controllers (admin, auth, directory surfaces), DTOs, bean-validation, RFC 7807
  errors. Hosts the token endpoint and the JWK-set endpoint.
- **`application`** — use cases (provision tenant; create/deactivate caregiver; authenticate & issue
  token; link/unlink caregiver↔patient; resolve directory queries), transaction boundaries, the
  `Tenant` provisioning state machine, and the `role → authorities` policy.
- **`domain`** — `Tenant`, `Cuidador`, `CaregiverPatientLink` aggregates; `Papel`, `TenantTipo`,
  `Credential` (hash), `Authority` value objects; invariants (single-tenant scoping, valid role,
  same-tenant linkage). Pure Java, no Spring/JPA.
- **`infrastructure`** — JPA entities/repositories (global + tenant-schema resolution), the JWT signer
  / key store, password hasher, inbound REST adapters, and **outbound ports**: `MigProvisioningPort`
  (request schema from MIG) and `HistAuditPort` (send audit facts). All REST over Eureka/GWY.

## Cross-cutting Concerns
- **Security:** ACS is the OAuth2 **authorization server** for the platform (issues JWT, publishes JWK
  set; RAG-confirmed pattern: peers are resource servers with `jwk-set-uri` reachable via REG). Its own
  admin/directory endpoints are also `@PreAuthorize`-guarded; a caregiver can only administer within
  their tenant, and only `coordenador_clinica` may create accounts/linkages. Credentials stored hashed
  (e.g. bcrypt/argon2). `@AuditAction` on all CREATE/UPDATE/DELETE (RBAC.002).
- **Multi-tenancy:** ACS is the **provisioner** and enforcer. The tenant directory lives in
  `neuroassist_global`; caregiver/linkage rows are tenant-scoped. `EmpresaContextHolder.require()` at
  every application-service entry (MT.001); `search_path` set per connection (MT.002/003). Every token
  carries `tenantId`; every downstream filter derives from it (constitution entity-scope).
- **Observability:** correlation id propagated (`X-Correlation-Id`); `tenantId` and `cuidadorId` on the
  MDC for every request; auth failures and provisioning steps logged as first-class events. RFC 7807
  error model (EM.001–004).
- **LGPD / privacy:** identity + access metadata only; no biometric/clinical data. Data-subject rights
  (delete/export a caregiver's account) handled here as the tenant/identity owner, not by ad-hoc
  deletes in other services.
