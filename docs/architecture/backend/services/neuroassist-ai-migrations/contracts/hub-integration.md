# MIG — Hub / Platform Integration

> MIG has **no TechLab AI hub dependency** (`ai.hasAi = false`) and is **not
> on the request path** (so it does not depend on GWY functionally). Its real
> platform relationships are: a privileged PostgreSQL connection, a **read**
> of the tenant registry, an optional Eureka registration, and being the
> **synchronous callee** of ACS at empresa onboarding.

## 1. PostgreSQL (the only hard runtime dependency)

| Aspect | Value |
|---|---|
| Role | Privileged DDL role (e.g. `neuroassist_migrator`) able to `CREATE SCHEMA`, run cross-schema DDL, and write each schema's `flyway_schema_history` |
| Connection | Direct JDBC to the single NeuroAssist AI PostgreSQL instance (A2 — no sharding) |
| Scope | Cross-schema — the only component that legitimately holds cross-schema DDL reach (doc 05) |
| Lifecycle | Connection opened for the run, closed at completion (job model) |

`THE SYSTEM SHALL connect to PostgreSQL with a privileged role able to CREATE SCHEMA and run cross-schema DDL.`
`THE SYSTEM SHALL be the only platform component that holds cross-schema DDL reach.`

## 2. Tenant registry (read-only, at migration time)

MIG must know the full set of tenant schemas to fan out over `migrate-all`.
It reads the `tenant` table (data-model §2.2) directly via its privileged
connection — it does not call a service.

`THE SYSTEM SHALL resolve the set of tenant schemas by reading the tenant table in neuroassist_global at migration time.`
`IF the tenant registry is unreadable, THEN THE SYSTEM SHALL fail pre-flight (exit 3) before applying any DDL.`

## 3. Eureka / Service Registry (REG)

MIG's core job needs no Eureka (it is not on the request path). It registers
its internal HTTP face so ACS and operators can discover it (infra topology
convention — `04-backend-architecture-direction.md`).

`THE SYSTEM SHALL register with Eureka (REG) so ACS can discover its internal provisioning endpoint.`
`THE SYSTEM SHALL NOT make Eureka registration a prerequisite of its core migration job (the job runs standalone too).`

## 4. API Gateway (GWY)

None. MIG is never routed through GWY and exposes nothing on the public
`/api/**` edge.

`THE SYSTEM SHALL NOT be exposed through the API gateway.`

## 5. ACS (Cuidadores e Acesso) — the provisioning caller

ACS is the origin of the tenant-provisioning trigger at empresa onboarding
(doc 06, G4/Dependências Críticas). The contract is the internal HTTP call
defined in `api-contract.md` §1.1: ACS calls
`PUT /internal/migrations/tenants/{tenantId}` synchronously and waits for a
fully-migrated schema before completing onboarding.

`THE SYSTEM SHALL expose a synchronous provisioning endpoint that ACS calls at empresa onboarding and waits on until the tenant schema is created and migrated to head.`

## 6. Business modules (packaging-time, not runtime)

PAC/ACS/HIST/EVNT/EDGE depend on MIG **temporally**: they assume schemas are
migrated before they boot, not via a runtime call (ADR-002). Once each module
goes through `/be-architect-specs`, it contributes its own tenant-template
migrations (data-model §5); MIG runs them but authors none of its own beyond
the initial baseline.

`THE SYSTEM SHALL apply migrations before the business services roll out, so they boot against already-migrated schemas.`

## 7. TechLab AI hub

None — see `techlab-ai-integration.md` (N/A).
