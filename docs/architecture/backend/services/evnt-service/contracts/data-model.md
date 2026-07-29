# EVNT — Data Model (`evnt-service`)

> Module: **EVNT — Eventos e Alarmes** · Service: `evnt-service` · Package base: `com.neuroassist`
> Multi-tenant **schema-per-empresa** (ADR-003 shared). EVNT business tables live in each tenant schema
> `t_<empresa>`; the global schema (`neuroassist_global`) holds no EVNT business data.
> Migrations are authored here and applied by the **centralized MIG service** (ADR-003 shared) — no
> boot-time DDL in evnt-service. The `sql` blocks below are the canonical DDL: `be-migration-generator.py`
> extracts them in order into `V<n>__evnt_*.sql`.

## 1. Conventions

- **PK strategy:** `uuid` server-generated (`gen_random_uuid()`, `pgcrypto`), never client-trusted (except
  `report_id`, which is the client's idempotency key and is stored as a business column, not the PK).
- **Timestamps:** `timestamptz`, UTC. Every table carries `created_at`; mutable tables carry `updated_at`.
- **Soft-delete:** none — Eventos are durable operational + audit-source records; verdicts are terminal
  (ADR-006), not deletes. Tenant off-boarding drops the whole `t_<empresa>` schema.
- **Enums:** modeled as `varchar` + `CHECK` (portable across the MIG-managed schemas; no Postgres enum types
  to keep tenant-schema migrations simple).
- **Cross-service refs** (`paciente_id`, `comando_ref`, `confirmado_por`, `target_caregiver_id`) are **not
  FK-enforced** — they point at PAC/ACS-owned data in other services (snapshot-by-reference, ADR-004).
- **Schema routing:** the first line of each `sql` block declares its target schema via a SQL comment
  (`-- schema: empresa` for tenant tables, `-- schema: global` for extensions/global).

## 2. Entities

### 2.0 Extensions

Global, applied once; `pgcrypto` provides `gen_random_uuid()`.

```sql
-- schema: global
CREATE EXTENSION IF NOT EXISTS pgcrypto;
```

### 2.1 `evento` [empresa]

The system of record for a recognized help request (ADR-004). Borrowed facts (command label, urgency,
timing) are **snapshotted** at creation and never re-read, so a later `Comando` edit in PAC does not mutate
an existing Evento. `report_id` is unique per tenant schema — the persistence-level idempotency guard.

```sql
-- schema: empresa
CREATE TABLE evento (
    id                       uuid         NOT NULL DEFAULT gen_random_uuid(),
    paciente_id              uuid         NOT NULL,
    comando_ref              varchar(128) NOT NULL,
    command_label_snapshot   varchar(200) NOT NULL,
    urgencia_snapshot        varchar(16)  NOT NULL,
    report_id                uuid         NOT NULL,
    recognized_at            timestamptz  NOT NULL,
    timing_start_ms          bigint       NOT NULL,
    timing_end_ms            bigint       NOT NULL,
    timing_duration_ms       integer      NOT NULL,
    confidence               numeric(4,3) NOT NULL,
    feedback                 varchar(16)  NOT NULL DEFAULT 'nao_avaliado',
    confirmado_por           uuid         NULL,
    confirmado_at            timestamptz  NULL,
    correlation_id           varchar(64)  NULL,
    seq                      bigserial    NOT NULL,
    created_at               timestamptz  NOT NULL DEFAULT now(),
    updated_at               timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (report_id),
    CHECK (urgencia_snapshot IN ('emergencia','rotina')),
    CHECK (feedback IN ('nao_avaliado','real','falso_positivo')),
    CHECK (timing_duration_ms >= 0 AND timing_end_ms >= timing_start_ms),
    CHECK (confidence >= 0 AND confidence <= 1),
    CHECK ((feedback = 'nao_avaliado' AND confirmado_por IS NULL AND confirmado_at IS NULL)
        OR (feedback IN ('real','falso_positivo') AND confirmado_por IS NOT NULL AND confirmado_at IS NOT NULL))
);

CREATE UNIQUE INDEX ux_evento_report_id ON evento (report_id);
CREATE INDEX ix_evento_paciente_recognized ON evento (paciente_id, recognized_at DESC);
CREATE INDEX ix_evento_open ON evento (recognized_at DESC) WHERE feedback = 'nao_avaliado';
CREATE INDEX ix_evento_falsepositive ON evento (comando_ref) WHERE feedback = 'falso_positivo';
CREATE INDEX ix_evento_seq ON evento (seq);
COMMENT ON TABLE evento IS 'EVNT system-of-record for a recognized help request (ADR-004); tenant-scoped; verdict terminal (ADR-006).';
COMMENT ON COLUMN evento.report_id IS 'Client-generated idempotency key from the edge RecognizedCommandReport (ADR-004).';
COMMENT ON COLUMN evento.seq IS 'Monotonic per-schema sequence powering SSE Last-Event-ID resume (api-contract 3.3).';
```

**Invariants:**
- `UNIQUE (report_id)` is the persistence-level *one report ⇒ one Evento* guard (Framing invariant #1).
- The compound `CHECK` on `feedback`/`confirmado_por`/`confirmado_at` enforces *feedback is terminal and
  attributed* (ADR-006, Framing invariant #5): a verdict is impossible without a confirmer + timestamp.
- `urgencia_snapshot` is immutable after insert (never updated by the app) — *urgency is snapshotted, not
  live* (Framing invariant #3). The verdict transition updates only `feedback`, `confirmado_por`,
  `confirmado_at`, `updated_at`.

### 2.2 `notification_dispatch` [empresa]

A dispatch record per notification attempt for an Evento (ADR-005). Tier is a deterministic function of the
Evento's `urgencia_snapshot`. Status is tracked so a fail-open retry sweep can re-queue outstanding
dispatches after a restart (doc 06 risk #4).

```sql
-- schema: empresa
CREATE TABLE notification_dispatch (
    id                   uuid         NOT NULL DEFAULT gen_random_uuid(),
    evento_id            uuid         NOT NULL,
    tier                 varchar(20)  NOT NULL,
    target_caregiver_id  uuid         NOT NULL,
    status               varchar(16)  NOT NULL DEFAULT 'queued',
    attempts             integer      NOT NULL DEFAULT 0,
    last_attempt_at      timestamptz  NULL,
    last_error           varchar(300) NULL,
    created_at           timestamptz  NOT NULL DEFAULT now(),
    updated_at           timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (tier IN ('full_screen_alarm','discreet')),
    CHECK (status IN ('queued','delivered','acknowledged','failed')),
    CHECK (attempts >= 0),
    FOREIGN KEY (evento_id) REFERENCES evento (id) ON DELETE CASCADE
);

CREATE INDEX ix_dispatch_evento ON notification_dispatch (evento_id);
CREATE INDEX ix_dispatch_retry ON notification_dispatch (created_at) WHERE status IN ('queued','failed');
COMMENT ON TABLE notification_dispatch IS 'Per-attempt notification delivery record (ADR-005); retry sweep reads status queued/failed.';
```

**Invariants:**
- `tier` is derived, never free — `emergencia → full_screen_alarm`, `rotina → discreet` (ADR-005). The app
  sets it from `evento.urgencia_snapshot`; the `CHECK` bounds the vocabulary.
- The partial index `ix_dispatch_retry` is the *emergency-never-lost* retry sweep source (invariant #4).
- FK to `evento` is same-schema and safe (both tenant-scoped); `ON DELETE CASCADE` only fires on tenant
  off-boarding schema drop.

### 2.3 `report_idempotency` [empresa]

Explicit idempotency ledger keyed by `report_id`, mapping a processed report to its Evento so a duplicate
returns the original verdict cheaply without touching `evento`. **Decision (closes doc 06 gap #4):** keep a
**separate ledger** rather than relying solely on `evento`'s unique index — it lets a duplicate short-circuit
be answered with a single indexed lookup, records `first_seen_at` for the retention/idempotency-window
policy independently of the Evento, and keeps the ingress fast-path from contending on the `evento` table.

```sql
-- schema: empresa
CREATE TABLE report_idempotency (
    report_id      uuid         NOT NULL,
    evento_id      uuid         NOT NULL,
    first_seen_at  timestamptz  NOT NULL DEFAULT now(),
    PRIMARY KEY (report_id),
    FOREIGN KEY (evento_id) REFERENCES evento (id) ON DELETE CASCADE
);

CREATE INDEX ix_report_idempotency_evento ON report_idempotency (evento_id);
COMMENT ON TABLE report_idempotency IS 'One report => one Evento ledger (ADR-004); duplicate reportId returns the original evento_id.';
```

**Invariants:**
- `report_idempotency.report_id` and `evento.report_id` are written in the **same transaction** as the
  Evento insert; both being unique makes duplicate ingress a race-safe no-op (SPEC-05).
- Retention ≥ the edge retry/replay window (guidance ≥ 24h, aligned with GEST idempotency window); the pilot
  keeps it indefinitely.

## 3. Relations (ASCII)

```
                 (PAC)               (ACS)
             Comando/Paciente     Cuidador
                   ▲                  ▲
                   │ ref (snapshot)   │ ref (confirmado_por / target)
                   │                  │
  report_idempotency ──1:1──► evento ──1:N──► notification_dispatch
       (report_id PK)          (id PK)          (evento_id FK)
```

Only `evento ↔ notification_dispatch` and `evento ↔ report_idempotency` are real FKs (same schema). All
patient/command/caregiver references are opaque cross-service ids (not FK-enforced).

## 4. Migration plan

| Version | Script | Content |
|---|---|---|
| `V1` | `V1__evnt_extensions.sql` (global) | `pgcrypto` |
| `V2` | `V2__evnt_evento.sql` (empresa) | `evento` + indexes |
| `V3` | `V3__evnt_notification_dispatch.sql` (empresa) | `notification_dispatch` + indexes |
| `V4` | `V4__evnt_report_idempotency.sql` (empresa) | `report_idempotency` + index |

Reserved band `V0001..V0099` for EVNT initial schema; feature migrations continue from `V0100`. MIG applies
the `empresa`-schema scripts across every `t_<empresa>` at provisioning/upgrade; the `global` script runs
once against `neuroassist_global`.

## 5. Schema-level mapping

| Table | Schema |
|---|---|
| `evento` | `t_<empresa>` (tenant) |
| `notification_dispatch` | `t_<empresa>` (tenant) |
| `report_idempotency` | `t_<empresa>` (tenant) |
| _(extensions)_ | `neuroassist_global` (global) |

## 6. LGPD / privacy

No table stores image, frame, or facial-landmark data — only primitive ids (in the report, not persisted
here beyond `comando_ref`/`confidence`), timing, urgency, feedback, and cross-service references (Framing
invariant #6). Per-subject erasure is handled at the tenant/patient boundary and cascades within the schema.
