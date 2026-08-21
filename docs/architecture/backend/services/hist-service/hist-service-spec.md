---
module: HIST
service: hist-service
package: com.neuroassist.hist
version: "1.0.0"
status: specs-complete
port: 8089
adrs:
  - ADR-004-immutable-append-only-ledger.md
  - ADR-005-synchronous-audit-ingestion.md
  - ADR-006-hash-chained-tamper-evidence.md
generatedAt: "2026-08-16"
---

# hist-service Spec Entry Point

> Module: **HIST — Histórico e Auditoria** · Service: `hist-service`
> Package: `com.neuroassist.hist` · Multi-tenant: schema-per-empresa (`t_<empresa>`)
> Messaging: **disabled** (synchronous REST only) · AI: **none**
> Pipeline phase: **specs** complete (contracts + SPECs + Gherkin features + C4 diagrams)

## Purpose

`hist-service` is the **immutable audit ledger** of NeuroAssist AI — the trustworthy witness of *what
happened* across the platform: events raised and confirmed, commands recalibrated, access granted,
devices paired. It owns a single aggregate, the **`AuditRecord`**, and exposes two API surfaces:

1. **Ingestion surface** (`/api/v1/hist/audit-facts`) — producers EVNT, PAC, ACS and EDGE submit audit
   facts synchronously. Ingestion is **idempotent on a producer-minted `factId`** (ADR-005) and
   **write-once** (ADR-004): a record, once appended, is never updated or deleted through any code path.
2. **History surface** (`/api/v1/hist/history`) — DASH (and coordinators/DPOs via GWY) query the timeline
   by subject / factType / actor / time-window, and run **chain verification** to *prove* the trail is
   intact (ADR-006).

HIST is deliberately a **sink**, not a system-of-record. It mirrors facts owned by EVNT/PAC/ACS/EDGE and
never hosts a producer's operational logic; `subjectRef` and `actorRef` are opaque references, never
resolved into HIST tables. The ledger carries only structured business snapshots — **never biometric,
image, or facial-landmark data** (project-wide LGPD hard rule).

Tamper-evidence is provided by a per-`(tenant, subject)` **hash chain**
(`recordHash = sha256(prevHash ‖ canonical(record))`, ADR-006); a chain-verification read recomputes a
stream and returns *intact* or *broken@N*.

## Document map

| Document | Path | Status |
|---|---|---|
| API contract (barrier) | `contracts/api-contract.md` | ✅ |
| Data model + DDL | `contracts/data-model.md` | ✅ |
| Event contracts (messaging disabled) | `contracts/event-contracts.md` | ✅ |
| Integration contracts (ACS outbound) | `contracts/integration-contracts.md` | ✅ |
| Event catalog | `events/event-catalog.md` | ✅ |
| SPEC-01 API surface | `spec/backend/SPEC-01-api-surface.md` | ✅ |
| SPEC-02 Audit ingestion & chaining | `spec/backend/SPEC-02-audit-ingestion-chaining.md` | ✅ |
| SPEC-03 History query & chain verification | `spec/backend/SPEC-03-history-query-verification.md` | ✅ |
| SPEC-04 Data model + migrations | `spec/backend/SPEC-04-data-model-migrations.md` | ✅ |
| SPEC-05 Idempotency & concurrency | `spec/backend/SPEC-05-idempotency-concurrency.md` | ✅ |
| SPEC-06 Security | `spec/backend/SPEC-06-security.md` | ✅ |
| SPEC-07 Observability & health | `spec/backend/SPEC-07-observability-health.md` | ✅ |
| SPEC-08 BDD test scenarios | `spec/backend/SPEC-08-bdd-test-scenarios.md` | ✅ |
| SPEC-09 Messaging architecture | `spec/backend/SPEC-09-messaging-architecture.md` | ✅ |
| SPEC-10 Integrations | `spec/backend/SPEC-10-integrations.md` | ✅ |
| Gherkin features | `tests/cucumber/*.feature` | ✅ |
| C4 diagrams | `diagrams/c4-{context,container,component}.puml` | ✅ |
| Setup (local) | `setup-local.md` | ✅ |
| Generator config | `generator-config.yml` | ✅ |

## Traceability

Every spec references at least one ADR. The three governing decisions:

- **ADR-004** — immutable append-only ledger (write-once; corrections are new records).
- **ADR-005** — synchronous REST audit ingestion with producer-side durability (broker-deferred);
  idempotent on `factId`.
- **ADR-006** — hash-chained tamper-evidence per `(tenant, subject)` stream.

EARS notation (D-057) governs every testable invariant in the contracts and SPECs. Guide:
`docs/architecture/backend/standards/ears-notation.md`.
