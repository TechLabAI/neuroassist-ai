<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
Multi-tenant **schema-per-empresa** (ADR-003 shared). HIST's ledger tables live in each tenant schema
`t_<empresa>`; an `AuditRecord` and its idempotency entry are always tenant-scoped and never cross-schema.
The global schema (`neuroassist_global`) holds no HIST business data. Tenant resolution happens at request
time and pins the JPA/JDBC schema for the unit of work, so no cross-tenant query — nor a cross-tenant hash
chain — is representable. Because the ledger is append-only, the write path uses INSERT only; where the
DBMS permits, the ledger tables are granted to the service role **without UPDATE/DELETE privileges** as a
defense-in-depth backstop for immutability (ADR-004).

## Initial Entities
> Draft persistence shape; the authoritative data model is frozen in Specs (`contracts/data-model.md`).

### `audit_record` (tenant schema)
| Column | Type | Notes |
|---|---|---|
| `id` | uuid (PK) | server-generated |
| `fact_id` | uuid | producer-minted idempotency key; **unique** per tenant |
| `fact_type` | varchar | controlled vocabulary (e.g. `evento.confirmado`, `acesso.concedido`) |
| `subject_type` | varchar | `paciente` \| `comando` \| `cuidador` \| `dispositivo` |
| `subject_id` | uuid | opaque ref to the subject (owned by EVNT/PAC/ACS/EDGE) |
| `actor_caregiver_id` | uuid | ref → Cuidador (ACS); who performed the action |
| `actor_label_snapshot` | varchar | snapshot of caregiver display name at record time |
| `payload` | jsonb | structured, self-contained snapshot of the fact (no biometric data) |
| `occurred_at` | timestamptz | producer clock — when the action happened |
| `recorded_at` | timestamptz | HIST durable clock — when appended (authoritative order) |
| `stream_key` | varchar | `subject_type:subject_id` — the chain scope within the tenant |
| `sequence_no` | bigint | strictly monotonic position within `stream_key` |
| `prev_hash` | char(64) | previous record's `record_hash` in the stream (genesis constant for first) |
| `record_hash` | char(64) | `sha256(prev_hash ‖ canonical(record))` — tamper evidence |
| `corrects_record_id` | uuid | nullable; set only on corrective records (points to a prior record) |

Indexes: **unique** `(fact_id)` (idempotency); **unique** `(stream_key, sequence_no)` (chain ordering);
`(subject_type, subject_id, recorded_at)` for timeline queries; `(fact_type)` and `(actor_caregiver_id)` for
audit filters; `(recorded_at)` for time-window scans. No column is ever UPDATEd after INSERT.

### `audit_idempotency` (tenant schema)
Minimal ledger: `fact_id` (PK), `record_id`, `first_seen_at`. Guarantees one fact ⇒ one record and lets a
duplicate return the original verdict. May be folded into the `audit_record.fact_id` unique index if a
separate ledger proves redundant (decision deferred to Specs).

### `stream_head` (tenant schema, optional optimization)
`stream_key` (PK), `last_sequence_no`, `last_record_hash`, `updated_at`. A single-row-per-stream head that
lets the append path fetch the chain tip in O(1) instead of scanning; it is a derived cache of the last
`audit_record` in the stream, not a source of truth. May be dropped if the `(stream_key, sequence_no)` index
makes tip lookup cheap enough (decision deferred to Specs).

## Migration Strategy
Flyway is **centralized in the MIG service** (all schemas), not run standalone by hist-service (ADR-003
shared). hist-service authors versioned migration scripts (`V<n>__hist_<desc>.sql`) applied by MIG across
each `t_<empresa>` schema at provisioning/upgrade time. No `hist-service` boot-time DDL. New tenants get the
HIST tables when MIG materializes their schema. The immutability GRANT posture (no UPDATE/DELETE on
`audit_record`) is expressed as a migration so it travels with the schema.

## Data Lifecycle
- **Retention:** audit records are durable and retained for the pilot's lifetime — they are the accountability
  record. No automatic purge in the MVP; no archival tiering (out of scope, doc 06).
- **Immutability:** a record is terminal once written — no update, no delete via any API or service code path
  (ADR-004). A correction is a new appended record referencing the prior one; the original always remains.
- **Idempotency window:** `audit_idempotency` is kept at least as long as any producer's retry/replay window
  (guidance: ≥ 24h, aligned with the edge/EVNT idempotency windows); the pilot keeps it indefinitely for
  simplicity.
- **Integrity:** the hash chain per (tenant, subject) stream makes any post-hoc alteration or missing record
  detectable; a chain-verification read is the mechanism a coordinator/DPO uses to *prove* intactness (ADR-006).
- **LGPD / erasure tension:** the ledger holds only structured business references and snapshots — **no
  biometric or video data** (project-wide hard rule) — but it *does* hold patient/caregiver references and
  actor labels, which are personal data. Immutability conflicts with per-subject row deletion; the MVP
  reconciles this by realizing erasure at the **tenant/patient boundary** (tenant-schema drop at off-boarding
  removes all HIST records for that empresa), not by deleting individual ledger rows. Sub-tenant per-patient
  erasure while preserving the ledger is an open policy question flagged for Specs/legal (doc 06).
