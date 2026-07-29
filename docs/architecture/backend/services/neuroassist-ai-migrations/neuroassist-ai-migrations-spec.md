---
module: MIG
service: neuroassist-ai-migrations
package: com.neuroassist.migrations
classification: infrastructure
hasUi: false
isInfrastructure: true
persistence: ddl-runner
messaging: disabled
stage: specs
generatedBy: /be-architect-specs
sourceVision:
  - _brief.md
  - 01-architectural-vision.md
  - 04-backend-architecture-direction.md
  - 05-persistence-model.md
  - 06-risks-gaps-assumptions.md
adrRefs:
  - adr/ADR-002-centralized-migrations.md
  - adr/ADR-005-tenant-schema-provisioning.md
---

# MIG — neuroassist-ai-migrations — Specs Entry Point

> MIG is an **infrastructure / platform service** (`isInfrastructure: true`,
> `hasUi: false`). It owns no business domain and no business table. It is,
> however, the **single owner of DDL** for the whole platform (ADR-002):
> the global schema (`neuroassist_global`) and every per-tenant schema
> (`t_<tenantId>`, one per empresa — família ou clínica). Its data model and
> idempotency specs are therefore authoritative about **schema topology**, not
> about business entities, which remain owned by the business modules whose
> tables MIG runs (PAC, ACS, HIST, EVNT, EDGE, GEST).
>
> **Messaging is disabled platform-wide** (`stack.messaging.enabled = false`).
> MIG publishes no event to a bus; lifecycle facts (schema migrated, empresa
> provisionada, migração falhou) are recorded only to the pipeline activity
> trail (D-423), best-effort.

## What the migrations service does (one paragraph)

MIG is a **Spring Boot Flyway runner** that holds the versioned DDL for the
global schema (`neuroassist_global`) and the canonical tenant template, and
applies both — on deploy — via a schema-aware Flyway configuration that
targets `neuroassist_global` and, on demand, every existing `t_<tenantId>`
schema. It **provisions** a new tenant schema (create + baseline to head)
synchronously when invoked by ACS at empresa onboarding (ADR-005); it does
**not** automate tenant deprovisioning (manual, reviewed operation — G1).
It is **fail-fast**: a checksum drift or DDL error aborts the run for that
schema without auto-repair. It has **no runtime REST API for domain data**
and is **not routed through GWY** — its surface is limited to an internal
provisioning/status API used by ACS and by operators.

## Spec map

| Artifact | Purpose | Status |
|---|---|---|
| `contracts/api-contract.md` | Internal-only API: `POST /internal/migrations/tenants/{tenantId}` (provision), `GET /internal/migrations/status` (info); job `--mode` + exit codes; RFC 7807 errors; no public Swagger, no GWY route | authoritative |
| `contracts/data-model.md` | Schema topology (global + tenant template) + tenant registry (`tenant` table) + Flyway ledger; **no business table** | authoritative |
| `contracts/event-contracts.md` | **N/A** — no message broker (`messaging.enabled = false`); lifecycle facts logged to the pipeline activity trail (D-423) only | N/A (recorded) |
| `contracts/hub-integration.md` | PostgreSQL privileged role, tenant-registry read, optional Eureka registration, ACS as the provisioning caller | authoritative |
| `contracts/integration-contracts.md` | PostgreSQL (only external system) + deploy-pipeline gate + ACS onboarding caller | authoritative |
| `contracts/techlab-ai-integration.md` | TechLab AI dependency | **N/A** — `ai.hasAi = false` |
| `events/event-catalog.md` | **N/A** — no published/consumed event (recorded) | N/A |
| `spec/backend/SPEC-01-api-surface.md` | Internal admin/status API + job invocation contract (EARS) | authoritative |
| `spec/backend/SPEC-02-migration-orchestration.md` | Global + tenant-fan-out orchestration, ordering, run report (EARS) | authoritative |
| `spec/backend/SPEC-03-tenant-provisioning.md` | Synchronous, idempotent tenant provisioning (ADR-005) (EARS) | authoritative |
| `spec/backend/SPEC-04-data-model-migrations.md` | Schema topology, tenant registry, tenant template, Flyway ledger (EARS) | authoritative |
| `spec/backend/SPEC-05-idempotency-concurrency.md` | Re-run idempotency, advisory lock, checksum drift, fail-fast (EARS) | authoritative |
| `spec/backend/SPEC-06-security.md` | Privileged DDL role isolation, internal-only auth, no public exposure (EARS) | authoritative |
| `spec/backend/SPEC-07-observability-health.md` | Pipeline activity trail (D-423), Flyway `info`, health surface (EARS) | authoritative |
| `spec/backend/SPEC-08-bdd-test-scenarios.md` | BDD scenario index + fixtures (EARS refs) | authoritative |
| `spec/backend/SPEC-09-messaging-architecture.md` | **N/A** — no broker; documents the decision and the revisit condition | N/A (recorded) |
| `spec/backend/SPEC-10-integrations.md` | PostgreSQL + deploy pipeline + ACS; no other external system (EARS) | authoritative |
| `tests/cucumber/migration-orchestration.feature` | Global + tenant fan-out, ordering, run report | authoritative |
| `tests/cucumber/tenant-provisioning.feature` | Provision, idempotent re-provision, invalid tenant | authoritative |
| `tests/cucumber/idempotency-and-drift.feature` | Re-run no-op, advisory lock, checksum drift fail-fast | authoritative |
| `diagrams/c4-context.puml` | MIG + deploy pipeline + ACS + PostgreSQL + security/LGPD audit | authoritative |
| `diagrams/c4-container.puml` | Runner + Flyway adapter + tenant registry reader + admin surface + PostgreSQL | authoritative |
| `diagrams/c4-component.puml` | Hexagonal packages (api/application/domain/infrastructure) | authoritative |
| `setup-local.md` | DB connection + privileged role + env vars + run/provision/info commands | authoritative |

## EARS notation

Testable invariants in this spec set use EARS (D-057). Authoring guide:
`docs/architecture/backend/standards/ears-notation.md`.

## Next action

-> /be-architect-backlog neuroassist-ai MIG
