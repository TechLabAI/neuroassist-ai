# MIG — Integration Contracts

> MIG's external surface is intentionally tiny: **PostgreSQL is the only
> external system it talks to**, and the **deploy pipeline** is the primary
> driver that invokes it and gates on its result. There is no SaaS / PSP / AI
> integration and no message broker.

## 1. PostgreSQL (NeuroAssist AI primary database)

| Aspect | Value |
|---|---|
| Kind | Relational database (single instance, many schemas — A2) |
| Auth | Privileged DDL role via JDBC credentials (secret-managed; not the DML-only roles business services use) |
| Operations | `CREATE SCHEMA`, cross-schema DDL, `flyway_schema_history` writes |
| Rate limits / SLA | None imposed by MIG; bounded by DB lock contention during DDL |
| Failure handling | Pre-flight check (connectivity + role + registry) → exit 3 on failure; per-schema isolation so one schema's failure does not corrupt another's history; no automated rollback — operator repair + corrective forward migration for recovery |
| Locking | PostgreSQL advisory lock per target schema to prevent two concurrent MIG runs racing the same schema (SPEC-05) |

`THE SYSTEM SHALL perform a pre-flight check (DB connectivity, privileged role, tenant registry readability) before applying any DDL.`
`IF the pre-flight check fails, THEN THE SYSTEM SHALL exit 3 and apply no DDL.`
`THE SYSTEM SHALL isolate each schema's apply so a failure on one schema does not corrupt another schema's history.`
`THE SYSTEM SHALL NOT attempt automated down-migration or rollback.`

## 2. Deploy pipeline (primary driver, not an "external API")

The CI/CD deploy pipeline runs MIG as a **gating job** before the
business-service rollout.

| Aspect | Value |
|---|---|
| Invocation | `--mode=migrate-all` (or `migrate-global`) as a run-to-completion job |
| Gate | Pipeline blocks the business-service deploy on MIG exit `0`; non-zero halts rollout |
| Artifacts | Consumes MIG's JSON run report (api-contract §3) for visibility |
| Provisioning | `provision-tenant` invoked by the ACS onboarding flow (not the deploy job) |

`THE SYSTEM SHALL be runnable as a run-to-completion job whose exit code gates the business-service rollout.`
`WHEN MIG exits non-zero, THE SYSTEM SHALL cause the deploy pipeline to halt the business-service rollout.`

## 3. ACS onboarding flow (caller of provision)

| Caller | Operation | Contract |
|---|---|---|
| ACS (empresa onboarding) | `PUT /internal/migrations/tenants/{tenantId}` (or `provision-tenant --tenant=<id>` job path) | Synchronous; caller waits until schema is created + migrated to head (ADR-005) |

`THE SYSTEM SHALL provide a synchronous provision operation ACS can wait on until the tenant schema is created and migrated to head.`

## 4. No message broker

`messaging.enabled = false` platform-wide. MIG declares no producer and no
consumer. See `contracts/event-contracts.md`.

`THE SYSTEM SHALL declare no Kafka/broker producer or consumer while stack.messaging.enabled is false.`

## 5. No other integrations

- **No external SaaS / PSP.**
- **No TechLab AI** (`techlab-ai-integration.md` = N/A).
- **No Elasticsearch client** — MIG emits structured logs/activity-trail
  entries (D-423) but holds no ES client and declares no index.

`THE SYSTEM SHALL integrate with PostgreSQL and the deploy pipeline only; it SHALL declare no PSP, AI, broker, or Elasticsearch client.`
