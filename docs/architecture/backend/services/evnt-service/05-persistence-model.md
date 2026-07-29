<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
Multi-tenant **schema-per-empresa** (ADR-003 shared). EVNT's operational tables live in each tenant
schema `t_<empresa>` — an Evento and its notification dispatches are always tenant-scoped and never
cross-schema. The global schema (`neuroassist_global`) holds no EVNT business data; tenant resolution
happens at request time and pins the JPA/JDBC schema for the unit of work. No cross-tenant query is
representable.

## Initial Entities (draft)
> Draft persistence shape; the authoritative data model is frozen in Specs (`contracts/data-model.md`).

### `evento` (tenant schema)
| Column | Type | Notes |
|---|---|---|
| `id` | uuid (PK) | server-generated |
| `paciente_id` | uuid | ref → Paciente (PAC); not FK-enforced across services |
| `comando_ref` | varchar | opaque handle to `Comando` (PAC); EVNT does not resolve internals |
| `command_label_snapshot` | varchar | snapshot of the family's label at creation |
| `urgencia_snapshot` | varchar | `emergencia` \| `rotina` — snapshotted, never re-read |
| `report_id` | uuid | idempotency key (unique per tenant) |
| `recognized_at` | timestamptz | from the report's `recognizedAt` |
| `timing_start_ms` / `timing_end_ms` / `timing_duration_ms` | int | timing window |
| `feedback` | varchar | `nao_avaliado` \| `real` \| `falso_positivo` (default `nao_avaliado`) |
| `confirmado_por` | uuid | ref → Cuidador (ACS); nullable until confirmed |
| `confirmado_at` | timestamptz | nullable until confirmed |
| `created_at` / `updated_at` | timestamptz | audit metadata |

Indexes: **unique** `(report_id)` (idempotency); `(paciente_id, recognized_at)` for history/routing;
`(feedback)` partial for open (`nao_avaliado`) events and for false-positive-rate queries.

### `notification_dispatch` (tenant schema)
| Column | Type | Notes |
|---|---|---|
| `id` | uuid (PK) | |
| `evento_id` | uuid | FK → `evento.id` (same schema) |
| `tier` | varchar | `full_screen_alarm` \| `discreet` (derived from urgency) |
| `target_caregiver_id` | uuid | ref → Cuidador (ACS) |
| `status` | varchar | `queued` \| `delivered` \| `acknowledged` \| `failed` |
| `attempts` | int | retry counter |
| `last_attempt_at` | timestamptz | |
| `created_at` | timestamptz | |

Index: `(status)` partial for `queued`/`failed` (the retry sweep).

### `report_idempotency` (tenant schema)
Minimal ledger: `report_id` (PK), `evento_id`, `first_seen_at`. Guarantees one report ⇒ one Evento and
lets a duplicate return the original verdict. May be folded into the `evento.report_id` unique index if
a separate ledger proves redundant (decision deferred to Specs).

## Migration Strategy
Flyway is **centralized in the MIG service** (all schemas), not run standalone by evnt-service (ADR-003
shared). evnt-service authors versioned migration scripts (`V<n>__evnt_<desc>.sql`) applied by MIG across
each `t_<empresa>` schema at provisioning/upgrade time. No `evnt-service` boot-time DDL. New tenants get
the EVNT tables when MIG materializes their schema.

## Data Lifecycle
- **Retention:** Eventos are durable and retained for the pilot's lifetime; they are operational records
  and also the source of the audit facts mirrored to HIST. No automatic purge in the MVP.
- **Immutability of verdict:** once `feedback` is set (`real`/`falso_positivo`) it is terminal; any later
  correction is a new audited action, not an in-place edit.
- **Idempotency window:** the report-idempotency ledger is kept at least as long as the edge's retry/replay
  window (guidance: ≥ 24h, aligned with the GEST detection-contract idempotency window); pilot keeps it
  indefinitely for simplicity.
- **Archival / LGPD:** Eventos contain only business references, urgency, timing and feedback — **no
  biometric or video data** (project-wide hard rule). Tenant off-boarding drops the tenant schema, removing
  all EVNT data for that empresa; per-subject erasure (LGPD) is handled at the tenant/patient boundary and
  cascades to EVNT tables within the schema.
