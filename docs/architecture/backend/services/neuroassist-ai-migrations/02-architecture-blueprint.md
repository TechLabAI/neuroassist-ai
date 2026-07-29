<!-- OUTPUT-SCHEMA
- System Boundary
- External Actors and Systems
- End-to-End Flows
- Internal Boundaries
- Cross-cutting Concerns
-->

# 02 — Architecture Blueprint

## System Boundary
MIG is a standalone Spring Boot infrastructure service (`neuroassist-ai-migrations`) whose only
external dependency is the PostgreSQL instance. Inside its boundary it owns: the Flyway
configuration, the migration script sets (global + tenant template), and the tenant-provisioning
operation. Outside its boundary lie the business services (PAC, EVNT, ACS, HIST, DASH, EDGE,
GEST) and the infra services (GWY gateway, REG registry). It has **no UI** and **no domain data
API**; it manipulates schema, not domain records.

## External Actors and Systems
- **PostgreSQL** — the single database instance; target of all DDL. Owns `neuroassist_global`
  and every `t_<tenantId>` schema.
- **Deploy pipeline / operator** — runs MIG on release to bring schemas to head.
- **ACS (Cuidadores e Acesso)** — at empresa onboarding, triggers tenant provisioning
  synchronously (broker disabled; REST/sync per stack notes).
- **Service Registry (REG / Eureka)** — MIG registers for discovery and health, consistent with
  the monorepo topology.
- **Business services** — passive consumers: they assume MIG has already migrated their schema.

## End-to-End Flows
- **Golden path — global migration on deploy:** operator/pipeline starts MIG → Flyway resolves
  pending migrations for `neuroassist_global` → validates checksums → applies in order → records
  history → reports success. On checksum drift, aborts before applying (fail-fast).
- **Golden path — tenant provisioning (new empresa):** ACS requests provisioning for
  `<tenantId>` → MIG creates schema `t_<tenantId>` if absent → applies the full tenant migration
  history to head → records per-schema history → returns "Empresa provisionada". Re-invocation
  for an already-provisioned tenant is a no-op past head (idempotent).
- **Alternative — migrate all existing tenants:** on a new tenant-schema migration version, MIG
  iterates the tenant registry in the global schema and brings each `t_<tenantId>` to head, one
  schema at a time, failing fast on the first error and reporting which schema failed.
- **Alternative — status query:** operator runs `info` → MIG reports current version and pending
  migrations per schema.

## Internal Boundaries
Even as an infra service, MIG follows the monorepo hexagonal convention (ADR-001):
- **api/** — the thin invocation surface (provisioning trigger + status), no domain endpoints.
- **application/** — orchestration: "migrate global", "provision tenant", "migrate all tenants".
- **domain/** — the schema-lifecycle model (Schema, MigrationTarget, tenant registry view); no
  business aggregates.
- **infrastructure/** — Flyway adapter, DataSource/schema-switching adapter, tenant-registry
  reader, Eureka client. All I/O is confined here.

## Cross-cutting Concerns
- **Security:** MIG holds elevated DDL privileges; its credentials are the most sensitive in the
  platform and must be isolated from runtime service credentials (least privilege — business
  services get DML-only roles).
- **Multi-tenancy:** enforced structurally — one canonical tenant template, one schema per
  empresa, no cross-schema DDL from business services (ADR-003).
- **Observability:** every migration run emits structured logs + pipeline activity (D-423) with
  per-schema version and outcome; Flyway history is the durable audit trail.
- **LGPD:** retention/erasure DDL is expressed as reviewed migrations, keeping the data-lifecycle
  policy versioned and auditable.
- **Idempotency & concurrency:** provisioning is safe to retry; a schema-level advisory lock
  prevents two MIG runs from migrating the same schema concurrently.
