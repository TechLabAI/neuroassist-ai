<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

> **Note.** GWY is a **stateless infrastructure** service. It owns **no database schema**,
> is **not** a Flyway migration target, and stores no business data. This document records
> that deliberate absence and the only ephemeral/config state the gateway holds, so Specs
> and Scaffold do not accidentally provision persistence for it.

## Schema Strategy
- **No schema.** The gateway participates in the project's schema-per-empresa model only
  as a *propagator*: it derives `empresa_id` from the authenticated principal and forwards
  it as `X-Empresa-Id`. It never opens a tenant schema, never runs a query, and holds no
  connection pool to Postgres.
- The `neuroassist_global` schema and every `emp_*` tenant schema are owned by the domain
  services and materialised by MIG (centralized Flyway); the gateway is unaware of them.

## Initial Entities
None persisted. The only stateful runtime artefacts are ephemeral and non-durable:

- **Rate-limit counters** — token-bucket state per route/principal. In-memory for the MVP
  (lost on restart, acceptable). Redis-backed only if a distributed counter is later
  required (out of MVP scope).
- **JWK cache** — cached public keys fetched from ACS, TTL-bounded, refreshed on rotation.
  Pure cache; safe to lose.
- **Route/rate-limit configuration** — loaded from `application.yml` / environment at
  startup; configuration, not data.

## Migration Strategy
- **Not a Flyway target.** The gateway contributes **zero** migrations to MIG. No
  `V*.sql`, no per-tenant DDL. Adding or scaling the gateway never triggers a migration.
- Route and policy changes ship as configuration/deploy changes, versioned in the repo —
  not as database migrations.

## Data Lifecycle
- **Retention:** none for business data (there is none). Rate-limit and JWK state are
  transient with second/minute-scale lifetimes and no retention obligation.
- **Access logs:** the gateway emits structured access lines (correlationId, empresaId,
  route, status, latency) to the platform log pipeline; their retention is governed by the
  observability/infra layer, not by the gateway, and must respect LGPD (no payload bodies,
  no PII beyond tenant/principal ids in logs — see 06 risks).
- **Backup/restore:** not applicable — the gateway is fully reconstructable from its image
  and configuration; there is nothing to back up.
