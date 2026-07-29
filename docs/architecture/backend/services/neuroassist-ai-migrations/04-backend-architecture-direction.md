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
- **Java 21**, **Spring Boot 3.5.x** (monorepo canonical).
- **Flyway** as the migration engine (community edition; versioned `V__` + repeatable `R__`).
- **PostgreSQL 15+**, single instance, schema-per-tenant isolation.
- **Spring Cloud Netflix Eureka client** to register with REG (infra topology consistency).
- Package base: `com.neuroassist` (→ `com.neuroassist.migrations`).
- No message broker (Artemis/RabbitMQ) — `messaging.enabled = false`.
- No TechLab AI dependency — `ai.hasAi = false`.

## Service Boundary
Single service (`neuroassist-ai-migrations`) that centralizes **all** migrations for the
platform — the explicit product decision "Centralized Flyway (all schemas)" (see ADR-002 local:
centralized migrations vs per-service Flyway; default = centralized). It is the only component
with DDL authority. It exposes no domain data; its surface is limited to provisioning and status.

## Package Organization
Hexagonal (ADR-001), even for an infra service:
```
com.neuroassist.migrations
├── api/               # thin trigger + status surface (provision tenant, info); no domain endpoints
├── application/       # use cases: MigrateGlobal, ProvisionTenant, MigrateAllTenants, ReportStatus
├── domain/            # MigrationTarget, TenantRegistryEntry (read model), schema-lifecycle model + ports
└── infrastructure/    # FlywayAdapter, SchemaSwitchingDataSource, TenantRegistryReader, EurekaConfig
```
Ports live in `domain` (e.g. `MigrationRunner`, `TenantRegistry`); adapters in `infrastructure`.

## Hub Service Dependencies
- **REG (Eureka)** — MIG registers for discovery + health (infra convention). Not a functional
  dependency of the migration itself.
- **GWY (Gateway)** — MIG is an internal operational service; it is not intended to be exposed
  publicly through the gateway. Any provisioning trigger is an internal (service-to-service)
  call, not a public API.
- No dependency on any business service. Business services depend on MIG **temporally** (schema
  must be at head before they boot), not via a runtime call.

## External Integrations
- **PostgreSQL** — the only external system. MIG needs a high-privilege role capable of
  `CREATE SCHEMA` and DDL across all schemas; this role is distinct from the DML-only roles
  handed to business services (least privilege).
- No third-party APIs, no AI providers, no external queues.

## Async vs Sync Decisions
- **Synchronous by default.** With the broker disabled, tenant provisioning is invoked
  synchronously by the onboarding flow (ACS) and returns success/failure inline; the caller
  waits for a fully-migrated schema before proceeding. This matches the platform's edge→backend
  REST/sync + local outbox retry posture.
- **Migrate-on-startup vs on-demand.** Global migration runs at service startup on deploy;
  tenant provisioning and "migrate all tenants" are on-demand operations. Both are guarded by a
  per-schema advisory lock to serialize concurrent runs.
- **No event publication.** Lifecycle facts (SchemaMigrado, EmpresaProvisionada, MigracaoFalhou)
  are logged to the pipeline activity trail (D-423), not emitted to a bus. Revisit only if a
  broker is later enabled (stack note: reconsider if edge/tenant volume grows).
