<!-- OUTPUT-SCHEMA
- Intent
- Problem Statement
- Personas
- MVP Scope
- Out of Scope
- Architectural Principles
- Success Signals
-->

# 01 — Architectural Vision

## Intent
Provide NeuroAssist AI with a single, authoritative owner of the database schema lifecycle.
The Migrations (MIG) service centralizes Flyway across the global schema (`neuroassist_global`)
and every per-tenant schema (`t_<tenantId>`), so no business service ever mutates schema on its
own. This guarantees a deterministic, auditable, and reproducible database state for a
healthtech, LGPD-bound, multi-tenant platform.

## Problem Statement
NeuroAssist AI is a schema-per-tenant platform: each empresa (família ou clínica) gets an
isolated schema, plus one shared global schema. Without a central owner, every microservice
would embed its own Flyway and race on shared objects, tenant schemas would drift, and new
empresa onboarding would have no deterministic "bring schema to head" step. In a healthtech
context this is unacceptable: uncontrolled DDL threatens tenant isolation, auditability, and
LGPD data-lifecycle guarantees. MIG removes that risk by making schema evolution a single,
versioned, fail-fast responsibility.

## Personas
- **Plataforma / Time interno de desenvolvimento (primary):** authors migrations, runs MIG on
  deploy, and triggers per-tenant provisioning. Needs deterministic ordering and clear failure
  on drift.
- **Security gate / Operações LGPD (secondary):** audits every schema change and retention DDL,
  and relies on schema boundaries as the tenant-isolation mechanism. Needs a readable, immutable
  migration history.

## MVP Scope
- Centralized Flyway service that owns **all** schemas (global + per-tenant).
- Global schema baseline and versioned migrations under `neuroassist_global`.
- A per-tenant schema template applied identically to every empresa schema.
- Deterministic, idempotent tenant provisioning: create schema `t_<tenantId>` + apply the full
  migration history to head.
- Run modes: migrate-on-startup and on-demand migrate/info; fail-fast on checksum mismatch.
- Operator-readable migration status (Flyway `info`).

## Out of Scope
- No business/domain logic and no runtime domain REST API.
- No zero-downtime / online schema-change orchestration (blue-green, expand-contract tooling).
- No large data backfills beyond repeatable migration scripts.
- No automatic destructive DDL (drop/rename) without an explicit, reviewed migration.
- No sharding or multi-database topology (single PostgreSQL, schema-per-tenant only).

## Architectural Principles
1. **Single owner of schema.** Only MIG runs DDL; business services boot read/write against
   already-migrated schemas.
2. **Deterministic and idempotent.** Same inputs → same schema state; re-running a provision is
   a no-op past head.
3. **Fail-fast, never silently drift.** Checksum mismatch aborts; no auto-repair in the pipeline.
4. **Tenant isolation is structural.** Every tenant schema derives from one canonical template;
   no per-tenant divergence outside recorded migrations.
5. **Auditable by construction.** The Flyway history table is the immutable record of every
   schema change (LGPD-relevant).
6. **Infrastructure, not domain.** MIG has no UI, no domain aggregates, and no TechLab AI or
   broker coupling.

## Success Signals
- Every service starts against a schema at head; zero self-migration in business services.
- New empresa onboarding provisions a fully-migrated tenant schema in one deterministic step.
- Global and all tenant schemas report the same expected version in Flyway `info`.
- A checksum-drift attempt fails the run instead of corrupting a schema.
- Auditors can reconstruct the full schema-change timeline from migration history alone.
