# SPEC-04 — Data Model + Migrations

> Service: `evnt-service` · Canonical DDL: `contracts/data-model.md` (executable `sql` blocks).
> Schema-per-empresa (ADR-003 shared); migrations authored here, applied by MIG.

## Objetivo
Freeze the persistence model: three tenant tables (`evento`, `notification_dispatch`, `report_idempotency`)
and the migration strategy consumed by the centralized MIG service.

## Escopo
- **In:** table shapes, constraints, indexes, schema routing, migration versioning, `seq` for SSE resume.
- **Out:** JPA mapping details, query implementation (SPEC-02), retention policy narrative (doc 05).

## Decisões técnicas
- All EVNT business tables live in `t_<empresa>`; global schema holds only extensions.
- `evento.report_id` UNIQUE + a separate `report_idempotency` ledger (doc 06 gap #4 **resolved: keep both**
  — the ledger answers duplicates with one indexed lookup and holds `first_seen_at` independently).
- Enums as `varchar` + `CHECK` (portable across MIG-managed schemas).
- The compound `CHECK` binds `feedback` to `confirmado_por`/`confirmado_at` — a verdict is impossible
  without attribution (ADR-006 enforced at the DB).
- `evento.seq bigserial` provides the monotonic per-schema sequence powering SSE `Last-Event-ID` (SPEC-03).
- Migrations run through MIG (no boot-time DDL). Reserved band `V0001..V0099`.

## Casos de borda
- New tenant provisioning → MIG materializes the three EVNT tables in the new `t_<empresa>`.
- Tenant off-boarding → schema drop cascades `notification_dispatch`/`report_idempotency` via same-schema FK.

## Critérios de aceite (EARS)
- THE SYSTEM SHALL persist `evento`, `notification_dispatch`, and `report_idempotency` only in the tenant
  schema `t_<empresa>`.
- THE SYSTEM SHALL enforce `UNIQUE (report_id)` on `evento` and on `report_idempotency` PK.
- IF an insert would set `feedback` to `real`/`falso_positivo` without a `confirmado_por` and
  `confirmado_at`, THEN THE SYSTEM SHALL reject it (DB `CHECK`).
- THE SYSTEM SHALL never run boot-time DDL; all schema changes SHALL be versioned scripts applied by MIG.
- THE SYSTEM SHALL assign each Evento a monotonic per-schema `seq` usable for SSE resume.
