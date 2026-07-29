# Data Model — GWY (API Gateway)

> **GWY owns no database schema and is NOT a Flyway/MIG migration target.** This document
> exists to make that absence explicit and machine-evident: there are intentionally **no
> executable ```sql``` blocks** here, so `be-migration-generator.py` contributes **zero**
> migrations for this service. See `05-persistence-model.md` and INV-6.

## 1. Persistence decision

- No `neuroassist_global` access, no `emp_*` tenant schema access, no connection pool to
  Postgres. The gateway is a **tenant propagator**, not a tenant store: it derives
  `empresa_id` from the authenticated principal and forwards it as `X-Empresa-Id`.
- Adding or scaling the gateway never triggers a migration. Route/policy changes ship as
  configuration/deploy changes versioned in the repo.

EARS:
- THE SYSTEM SHALL NOT open a database connection.
- IF any code path attempts a JDBC/R2DBC connection, THEN THE SYSTEM SHALL be considered
  defective (a smoke test asserts no datasource is auto-configured).

## 2. Ephemeral / in-memory runtime state (not persisted)

| Artefact | Nature | Lifetime | Loss on restart |
|---|---|---|---|
| Rate-limit token buckets | per route (+ optional per principal) counters | seconds | acceptable — resets to full budget (R-3: per-replica in MVP) |
| JWK cache | ACS public keys keyed by `kid` | TTL-bounded, refresh on rotation | acceptable — refetched from ACS |
| Route table / policies | loaded from `application.yml` / env at startup | process lifetime | reloaded from config |
| `GatewayPrincipal` | request-scoped auth context | single request | n/a |

EARS:
- WHILE the JWK cache entry for a `kid` is present and unexpired, THE SYSTEM SHALL validate
  tokens without contacting ACS.
- WHEN a token references an unknown `kid`, THE SYSTEM SHALL refresh the JWK set once
  before rejecting the token as `401`.

## 3. Migration plan

Not applicable. Reserved MIG ranges (`V0001..`) are owned by the domain services; the
gateway reserves **none**.

## 4. Schema-level mapping

| Table | Schema |
|---|---|
| _(none)_ | _(the gateway persists no table)_ |
