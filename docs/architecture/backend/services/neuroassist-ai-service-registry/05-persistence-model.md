<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

> REG is **stateless**. It owns no database and participates in no schema. This
> document records that decision explicitly so downstream phases (specs, backlog,
> scaffold, migrations) do not provision persistence for REG by mistake.

## Schema Strategy
- **No schema.** REG does not use PostgreSQL and holds no `neuroassist_global`
  or `t_<empresa>` tenant schema.
- The registry lives entirely in the Eureka server's in-memory data structures.
- REG is deliberately excluded from the `schema-per-empresa` multi-tenancy model;
  it operates at the platform layer, above tenancy.

## Initial Entities
- **None persisted.** The conceptual runtime objects (Application, Instance, Lease)
  are managed in memory by the Eureka engine and are never mapped to JPA entities
  or tables (see 03 — Initial Domain Framing).

## Migration Strategy
- **No Flyway migrations for REG.** The centralized `neuroassist-ai-migrations`
  service (MIG) owns all schema migrations for the platform; REG contributes none.
- No datasource is configured for this service; a scaffold that adds one is a defect.

## Data Lifecycle
- **Ephemeral by design.** Registry contents exist only while REG runs. A restart
  starts from an empty registry and is repopulated by client re-registration.
- **No retention, no archival, no backup** — there is nothing durable to retain.
- **No personal / LGPD-scoped data** ever resides in REG; the only data held is
  operational metadata (service names, hosts, ports, health URLs, lease timestamps).
