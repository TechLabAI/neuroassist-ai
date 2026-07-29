# MIG — Brief (structured input for the architecture pipeline)

> Source of truth for `/be-architect-specs`, `/be-architect-backlog`, `/be-architect-scaffold`.
> Module type: **infrastructure** (`isInfrastructure: true`) — not a business domain.

## 1. Canonical name and prefix
- Prefix: `MIG`
- Canonical name (paths): `neuroassist-ai-migrations`
- Display name: Migrations

## 2. Problem the tool solves
Centralize the schema lifecycle of NeuroAssist AI: a single Flyway-owning service applies and
versions DDL/DML across the global schema (`neuroassist_global`) and every per-tenant schema
(`t_<tenantId>`, one per empresa), so every other service starts against a known, migrated
database instead of self-migrating and racing on shared schemas.

## 3. Personas
- **Primary:** Time interno de desenvolvimento / plataforma — authors migrations, runs the
  service during deploy, provisions new tenant schemas.
- **Secondary:** Security gate / operações (LGPD) — audits schema changes, retention DDL, and
  ensures tenant data isolation via schema boundaries.

## 4. MVP scope (minimum viable)
- Single centralized Flyway service owning **all** schemas (global + per-tenant).
- Global schema baseline + versioned migrations (`neuroassist_global`).
- Per-tenant schema template applied to each empresa schema; deterministic, idempotent tenant
  provisioning (create schema + apply full history) callable at empresa onboarding.
- Migration on startup / on-demand run mode; clean fail-fast on checksum drift.
- Migration status/history readable (Flyway `info`) for operators.

## 5. Out of scope (MVP)
- No business logic, no runtime REST API for domain data (that lives in PAC/EVNT/etc.).
- No online/zero-downtime rolling schema changes or blue-green schema swaps.
- No data backfill jobs beyond migration-embedded repeatable scripts.
- No automatic destructive migrations (drop/rename) without an explicit reviewed migration.
- No cross-database sharding; single PostgreSQL instance, schema-per-tenant only.

## 6. Mandatory external integrations
- **PostgreSQL** (single instance) — the only external system; owns global + tenant schemas.
- **Service Registry (REG / Eureka)** — registers for discovery/health, consistent with the
  monorepo topology (infra service, no UI).
- Consumed indirectly by all business services (they assume schemas are migrated before boot).

## 7. TechLab AI dependencies / event dependencies
- TechLab AI services: **none** (`ai.hasAi = false`).
- Message broker: **disabled** (`stack.messaging.enabled = false`) → no event-based coupling.
  Tenant provisioning is invoked synchronously (edge→backend is REST/HTTPS sync with local
  outbox retry per stack notes); MIG exposes provisioning as a synchronous operation triggered
  by the tenant/access flow (ACS) at empresa onboarding.

## 8. Domain anchor event
- **"Schema migrado"** (schema migrated) — the central verb: a target schema (global or a tenant
  schema) is brought to the latest known version. Secondary anchor: **"Empresa provisionada"**
  (tenant schema created + migrated to head).
