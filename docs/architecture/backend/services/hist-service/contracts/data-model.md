# HIST — Data Model (`hist-service`)

> Module: **HIST — Histórico e Auditoria** · Service: `hist-service` · Package: `com.neuroassist.hist`
> Multi-tenant: schema-per-empresa (`t_<empresa>`) · Global schema: `neuroassist_global`
> Migrations: **centralized in the MIG service** (`neuroassist-ai-migrations`) — no boot-time DDL in
> hist-service (ADR-003 shared). hist-service authors versioned `V<n>__hist_<desc>.sql`; MIG applies them
> across each `t_<empresa>` schema at provisioning/upgrade.
> Traces ADR-004 (append-only, no UPDATE/DELETE), ADR-006 (hash chain per stream).

The `be-migration-generator.py` extracts the ```sql``` blocks below in order. Each block's first line
carries a `-- schema:` routing comment (`empresa` → `db/migration/empresa/`, `global` → `db/migration/global/`).

---

## 1. Conventions

- **PK strategy:** all HIST-owned surrogate keys are `uuid` generated with `uuid_generate_v7()`
  (time-ordered, index-friendly). Producer-minted keys (`fact_id`, `subject_id`, `actor_caregiver_id`)
  are stored as received.
- **Timestamps:** `timestamptz`, UTC. `occurred_at` = producer clock; `recorded_at` = HIST durable clock
  (authoritative order), `DEFAULT now()`.
- **No soft-delete, no UPDATE:** the ledger is append-only (ADR-004). There is no `deleted_at`, no
  `updated_at` — a written row is terminal. Corrections are new rows referencing `corrects_record_id`.
- **Immutability GRANT posture:** the service DB role is granted `INSERT, SELECT` on `audit_record` and
  `audit_idempotency` and **not** `UPDATE`/`DELETE` (defense-in-depth backstop, ADR-004). Expressed as a
  migration so it travels with the schema.
- **Encoding:** UTF-8; `payload` is `jsonb`.
- **Hash columns:** `char(64)` lowercase hex (SHA-256).
- **Tenant isolation:** all ledger tables live in `t_<empresa>`; no HIST business data in the global
  schema. No cross-tenant FK, no cross-tenant chain (ADR-003 shared).

---

## 0. Extensions

```sql
-- schema: global
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "pgcrypto";
```

---

## 2. Entities

### 2.1 `audit_record` [empresa]

The single aggregate: one write-once ledger entry per audit fact. Once inserted it is never updated or
deleted (ADR-004). `stream_key = subject_type || ':' || subject_id` is the hash-chain scope within the
tenant; `sequence_no` is strictly monotonic per stream and `record_hash = sha256(prev_hash ‖
canonical(record))` (ADR-006). `payload` carries only structured business data — never biometric/image
data (LGPD hard rule, enforced at the application layer and asserted here by comment).

```sql
-- schema: empresa
CREATE TABLE audit_record (
    id                    uuid         NOT NULL DEFAULT uuid_generate_v7(),
    fact_id               uuid         NOT NULL,
    fact_type             varchar(80)  NOT NULL,
    subject_type          varchar(20)  NOT NULL,
    subject_id            uuid         NOT NULL,
    actor_caregiver_id    uuid         NOT NULL,
    actor_label_snapshot  varchar(200) NOT NULL,
    payload               jsonb        NOT NULL,
    occurred_at           timestamptz  NOT NULL,
    recorded_at           timestamptz  NOT NULL DEFAULT now(),
    stream_key            varchar(60)  NOT NULL,
    sequence_no           bigint       NOT NULL,
    prev_hash             char(64)     NOT NULL,
    record_hash           char(64)     NOT NULL,
    corrects_record_id    uuid         NULL,
    CONSTRAINT pk_audit_record PRIMARY KEY (id),
    CONSTRAINT uq_audit_record_fact_id UNIQUE (fact_id),
    CONSTRAINT uq_audit_record_stream_seq UNIQUE (stream_key, sequence_no),
    CONSTRAINT uq_audit_record_hash UNIQUE (record_hash),
    CONSTRAINT ck_audit_record_subject_type
        CHECK (subject_type IN ('paciente','comando','cuidador','dispositivo')),
    CONSTRAINT ck_audit_record_sequence_no CHECK (sequence_no >= 0),
    CONSTRAINT fk_audit_record_corrects
        FOREIGN KEY (corrects_record_id) REFERENCES audit_record (id)
);

CREATE INDEX ix_audit_record_timeline
    ON audit_record (subject_type, subject_id, recorded_at DESC);
CREATE INDEX ix_audit_record_fact_type      ON audit_record (fact_type);
CREATE INDEX ix_audit_record_actor          ON audit_record (actor_caregiver_id);
CREATE INDEX ix_audit_record_recorded_at    ON audit_record (recorded_at DESC);
CREATE INDEX ix_audit_record_stream         ON audit_record (stream_key, sequence_no);

COMMENT ON TABLE audit_record IS
    'HIST immutable append-only audit ledger (ADR-004). Write-once; no UPDATE/DELETE. Hash-chained per (stream_key) (ADR-006). payload carries no biometric/image data (LGPD).';
COMMENT ON COLUMN audit_record.stream_key IS 'subject_type:subject_id — hash-chain scope within the tenant';
COMMENT ON COLUMN audit_record.record_hash IS 'sha256(prev_hash || canonical(record)) — tamper evidence';
COMMENT ON COLUMN audit_record.corrects_record_id IS 'Set only on corrective records; points to the prior record (ADR-004)';
```

**Invariants:**
- **Append-only:** no code path UPDATEs or DELETEs a row (ADR-004); enforced by domain/application design
  and by the GRANT posture in §4.
- **Exactly-once per fact:** `uq_audit_record_fact_id` guarantees one `fact_id` ⇒ one row.
- **Chain ordering:** `uq_audit_record_stream_seq` guarantees monotonic, gap-free `sequence_no` per stream
  under concurrent appends (a losing concurrent insert retries with the next `sequence_no`, SPEC-05).
- **Full attribution:** all of `fact_type`, `subject_*`, `actor_*`, `occurred_at`, `recorded_at` are
  `NOT NULL` — an incomplete fact is rejected, never partially stored.
- **Genesis:** the first record of a stream uses the genesis constant `prev_hash` (§3), enforced at the
  application layer.

### 2.2 `audit_idempotency` [empresa]

Minimal idempotency ledger: maps a `fact_id` to the record it produced so a duplicate submission returns
the original verdict without a second write (ADR-005). Kept separate from `audit_record` so the write
path can assert idempotency in one indexed lookup; a duplicate `fact_id` short-circuits before chaining.

```sql
-- schema: empresa
CREATE TABLE audit_idempotency (
    fact_id        uuid        NOT NULL,
    record_id      uuid        NOT NULL,
    first_seen_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_audit_idempotency PRIMARY KEY (fact_id),
    CONSTRAINT fk_audit_idempotency_record
        FOREIGN KEY (record_id) REFERENCES audit_record (id)
);

COMMENT ON TABLE audit_idempotency IS
    'fact_id -> record_id idempotency ledger (ADR-005). One fact => one record; duplicates return the original.';
```

**Invariants:**
- One `fact_id` per row (`pk`); `record_id` references an existing `audit_record`.
- Retained at least as long as any producer retry/replay window (≥ 24h; pilot keeps indefinitely, §5).

### 2.3 `stream_head` [empresa]

Single-row-per-stream head cache: lets the append path fetch the chain tip (`last_sequence_no`,
`last_record_hash`) in O(1) instead of scanning `audit_record`. It is a **derived cache**, not a source of
truth — it can be rebuilt from `audit_record` at any time. This table *is* updatable (it is not part of
the ledger); the immutability GRANT posture applies only to `audit_record` and `audit_idempotency`.

```sql
-- schema: empresa
CREATE TABLE stream_head (
    stream_key         varchar(60) NOT NULL,
    last_sequence_no   bigint      NOT NULL,
    last_record_hash   char(64)    NOT NULL,
    updated_at         timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT pk_stream_head PRIMARY KEY (stream_key)
);

COMMENT ON TABLE stream_head IS
    'Derived tip cache per (stream_key): O(1) append tip lookup (ADR-006). Rebuildable from audit_record; not a source of truth.';
```

**Invariants:**
- `last_sequence_no` / `last_record_hash` equal the tip of the corresponding `audit_record` stream after
  every successful append (updated in the same transaction as the append).
- Updated under row lock (`SELECT … FOR UPDATE`) to serialize appends on the stream tip (SPEC-05).

---

## 3. Canonicalization & genesis (referenced by hash chain)

- **Genesis constant** (`prev_hash` of the first record in any stream):
  `0000000000000000000000000000000000000000000000000000000000000000` (64 zeros).
- **Canonical form** `canonical(record)` = deterministic JSON of the chained fields, in this exact key
  order: `fact_id, fact_type, subject_type, subject_id, actor_caregiver_id, actor_label_snapshot,
  payload (RFC 8785 JCS canonical), occurred_at (ISO-8601 UTC), stream_key, sequence_no, corrects_record_id`.
  `recorded_at`, `id`, `record_hash`, `prev_hash` are **excluded** from the canonical form (they are
  either derived or assigned by the chain).
- **Versioning:** the canonicalization is `v1`; if it ever changes, records store the version so
  verification stays stable over time (SPEC-02). For the MVP a single `v1` is assumed.

---

## 4. Immutability GRANT posture (migration)

```sql
-- schema: empresa
-- Defense-in-depth immutability backstop (ADR-004): the service role may INSERT and SELECT the ledger,
-- but never UPDATE or DELETE it. Applied per tenant schema by MIG. Role name is resolved by MIG at apply
-- time (placeholder ${hist_service_role}).
REVOKE UPDATE, DELETE ON audit_record       FROM PUBLIC;
REVOKE UPDATE, DELETE ON audit_idempotency   FROM PUBLIC;
GRANT  INSERT, SELECT ON audit_record        TO "${hist_service_role}";
GRANT  INSERT, SELECT ON audit_idempotency    TO "${hist_service_role}";
GRANT  INSERT, SELECT, UPDATE ON stream_head  TO "${hist_service_role}";
```

---

## 5. Data lifecycle

- **Retention:** durable for the pilot's lifetime; no automatic purge, no archival tiering (out of scope,
  vision doc 06).
- **Idempotency window:** `audit_idempotency` retained ≥ 24h (aligned with edge/EVNT idempotency windows);
  pilot keeps it indefinitely.
- **LGPD / erasure:** immutability conflicts with per-subject row deletion; the MVP realizes erasure at the
  **tenant/patient boundary** (tenant-schema drop at off-boarding removes all HIST records for that
  empresa), not by deleting ledger rows. **Sub-tenant per-patient erasure while preserving the chain is an
  open policy question flagged for Specs/legal** (crypto-shredding of `payload` while keeping the hashed
  skeleton is the candidate — deferred, SPEC-06 §Open). No biometric/image data is ever stored.

---

## 6. Relations (ASCII)

```
audit_idempotency.record_id ──────► audit_record.id      (one fact → one record)
audit_record.corrects_record_id ──► audit_record.id      (correction → prior record, self-FK)
stream_head.stream_key ── derived-from ── audit_record (stream_key, MAX(sequence_no))
```

Subjects (`paciente`, `comando`, `cuidador`, `dispositivo`) and actors (`cuidador`) are **opaque
references** owned by PAC/EVNT/ACS/EDGE — HIST holds no FK to them (they live in other services / schemas).

---

## 7. Migration plan (reserved ranges)

| Range | Purpose |
|---|---|
| `V0001__hist_extensions.sql` | §0 extensions (global). |
| `V0002__hist_audit_record.sql` | §2.1 `audit_record` + indexes (empresa). |
| `V0003__hist_audit_idempotency.sql` | §2.2 `audit_idempotency` (empresa). |
| `V0004__hist_stream_head.sql` | §2.3 `stream_head` (empresa). |
| `V0005__hist_immutability_grants.sql` | §4 GRANT posture (empresa). |
| `V0010+` | reserved for future factType-vocabulary / crypto-shredding evolution. |

## 8. Schema-level mapping

| Table | Schema |
|---|---|
| (extensions) | global (`neuroassist_global`) |
| `audit_record` | empresa (`t_<empresa>`) |
| `audit_idempotency` | empresa (`t_<empresa>`) |
| `stream_head` | empresa (`t_<empresa>`) |
