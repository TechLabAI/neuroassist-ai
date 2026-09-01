# CONV — Data Model (`conv-service`)

> Module **CONV** · Service `conv-service` · PostgreSQL 15+, schema-per-tenant (`empresa`).
> Canonical DDL lives in the executable ```` ```sql ```` blocks below — consumed by
> `be-migration-generator.py` (Flyway `V…__*.sql`) and `be-entity-generator.py` (JPA), shipped to the
> centralized **MIG** service (`neuroassist-ai-migrations`); CONV runs no local migrator (doc 05).
> Multi-tenancy per shared `ADR-003-multi-tenancy.md`. Bilingual model per `ADR-005` (→ ADR-018).

---

## 1. Conventions

- **PK strategy:** `uuid` primary keys (`gen_random_uuid()` via `pgcrypto`), application- or
  DB-generated. No natural keys as PK.
- **Timestamps:** `created_at` / `updated_at` `timestamptz NOT NULL DEFAULT now()`; UTC.
- **Soft-delete:** none in the MVP — tree edits are versioned through TREI's approval flow, not by CONV.
- **Encoding/collation:** UTF-8; `texto` columns are plain `text`.
- **Tenancy:** every CONV table lives in the **per-tenant schema** (`t_<empresa>`); the schema *is* the
  tenant boundary, so tables carry no `tenant_id` column. Rows are further scoped by `paciente_id`.
- **Cross-service references:** `paciente_id` references PAC's `Paciente` **by id only** — no FK across
  services (`ADR-013`; PAC stays system of record). Same for `engine_feature_ref` (PAC/GEST vocabulary).
- **Ownership:** CONV **reads** these tables at runtime and **writes** none of the content at runtime —
  authoring/approval writes come from **TREI** into the same schema (doc 05). CONV's own writes are
  limited to the audit outbox (§2.4).

---

## 0. Extensions

```sql
-- schema: global
CREATE EXTENSION IF NOT EXISTS pgcrypto;   -- gen_random_uuid()
```

---

## 2. Entities

### 2.1 `conv_arvore_fala` [empresa]

One row per patient speech tree. Aggregate root (`ArvoreFala`). Scopes the set of nodes; declares which
locales the tree supports.

```sql
-- schema: empresa
CREATE TABLE conv_arvore_fala (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    paciente_id         uuid        NOT NULL,               -- ref PAC.Paciente (no cross-service FK)
    locales_suportados  text[]      NOT NULL DEFAULT '{pt}',-- subset of {pt,en}
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    UNIQUE (paciente_id),                                   -- one logical tree per patient
    CHECK (locales_suportados <@ ARRAY['pt','en']::text[] AND array_length(locales_suportados,1) >= 1)
);

CREATE INDEX ix_conv_arvore_paciente ON conv_arvore_fala (paciente_id);
COMMENT ON TABLE conv_arvore_fala IS 'CONV speech tree, one per patient; tenant = schema (ADR-013).';
```

**Invariants:** exactly one tree per `paciente_id` (UNIQUE); `locales_suportados` ⊆ {pt,en} and non-empty.

### 2.2 `conv_no_arvore_fala` [empresa]

The tree nodes. Structure is **locale-invariant** (`ADR-005`): `parent_id`, `tipo`,
`engine_feature_ref`, `ordem` are single per node — only content (§2.3) varies by locale.

```sql
-- schema: empresa
CREATE TABLE conv_no_arvore_fala (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    arvore_id           uuid        NOT NULL,
    parent_id           uuid        NULL,                   -- null = root
    tipo                text        NOT NULL,               -- 'categoria' | 'frase'
    engine_feature_ref  text        NULL,                   -- opaque PAC/GEST vocabulary token (ADR-013, gap G4)
    ordem               integer     NOT NULL DEFAULT 0,     -- sibling order
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (tipo IN ('categoria','frase')),
    CHECK (parent_id IS NULL OR parent_id <> id),           -- no self-parent
    FOREIGN KEY (arvore_id) REFERENCES conv_arvore_fala (id) ON DELETE CASCADE,
    FOREIGN KEY (parent_id) REFERENCES conv_no_arvore_fala (id) ON DELETE CASCADE,
    UNIQUE (arvore_id, parent_id, ordem)                    -- deterministic sibling ordering
);

CREATE INDEX ix_conv_no_arvore     ON conv_no_arvore_fala (arvore_id);
CREATE INDEX ix_conv_no_parent     ON conv_no_arvore_fala (parent_id);
CREATE INDEX ix_conv_no_engine_ref ON conv_no_arvore_fala (engine_feature_ref) WHERE engine_feature_ref IS NOT NULL;
-- exactly one root per tree (parent_id IS NULL)
CREATE UNIQUE INDEX uq_conv_no_root ON conv_no_arvore_fala (arvore_id) WHERE parent_id IS NULL;
COMMENT ON TABLE conv_no_arvore_fala IS 'Speech-tree nodes; structure locale-invariant (ADR-005).';
```

**Invariants:**
- IF `parent_id IS NULL`, THEN THE SYSTEM SHALL treat the row as the tree root, and there SHALL be exactly one such row per `arvore_id` (`uq_conv_no_root`).
- `tipo ∈ {categoria, frase}`; only `frase` nodes are selectable at runtime (enforced in the application/API layer).
- No cycles / no orphans: every non-root node resolves to the root by following `parent_id` (a `categoria` may parent both `categoria` and `frase`; a `frase` is a leaf — leaf-ness is a domain invariant enforced in the service, not by DDL).
- `engine_feature_ref` is an **opaque** token; CONV never parses it (gap G4).

### 2.3 `conv_no_conteudo` [empresa]  — normalized per-locale content (resolves gap G2)

Per-locale content of a node, **normalized into its own table** rather than a JSON column, so each
locale can be activated, queried, and indexed independently (`ADR-005` left JSON-vs-normalized open;
**specs decides: normalized table**). Populated by TREI; read by CONV.

```sql
-- schema: empresa
CREATE TABLE conv_no_conteudo (
    id                    uuid        NOT NULL DEFAULT gen_random_uuid(),
    no_id                 uuid        NOT NULL,
    locale                text        NOT NULL,             -- 'pt' | 'en'
    texto                 text        NOT NULL,
    audio_ref             text        NULL,                 -- filesystem path/uri; null = not synthesized (ADR-004)
    audio_formato         text        NULL,                 -- 'mp3' | 'opus'
    confirmado_cuidadora  boolean     NOT NULL DEFAULT false,
    confirmado_paciente   boolean     NOT NULL DEFAULT false,
    status                text        NOT NULL DEFAULT 'pendente', -- 'ativo' | 'pendente'
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale IN ('pt','en')),
    CHECK (status IN ('ativo','pendente')),
    CHECK (audio_formato IS NULL OR audio_formato IN ('mp3','opus')),
    CHECK (audio_ref IS NULL OR audio_formato IS NOT NULL),  -- a ref must declare its format
    FOREIGN KEY (no_id) REFERENCES conv_no_arvore_fala (id) ON DELETE CASCADE,
    UNIQUE (no_id, locale)                                    -- one content row per (node, locale)
);

CREATE INDEX ix_conv_conteudo_no      ON conv_no_conteudo (no_id);
CREATE INDEX ix_conv_conteudo_locale  ON conv_no_conteudo (locale, status);
COMMENT ON TABLE conv_no_conteudo IS 'Per-locale node content (ADR-005); UNIQUE(no_id,locale); TREI writes, CONV reads.';
```

**Invariants:**
- UNIQUE `(no_id, locale)` — at most one content row per node per locale.
- IF `audio_ref` is present, THEN `audio_formato` SHALL be non-null (`mp3`|`opus`).
- CONV serves a node as speakable in a locale **only** WHERE a `conv_no_conteudo` row exists with that `locale` and `status = 'ativo'`; otherwise the node is `pending_locale` (api-contract §5). CONV never rewrites `status` at runtime — that transition is TREI's.

### 2.4 `conv_audit_outbox` [empresa]  — synchronous-best-effort audit buffer

CONV audits to HIST **synchronously** (`messaging.enabled = false`), but a HIST outage must not block a
patient's communication. This local outbox holds audit facts that failed the synchronous send, for
retry (SPEC-05/SPEC-09). It is the **only** table CONV writes at runtime.

```sql
-- schema: empresa
CREATE TABLE conv_audit_outbox (
    id               uuid        NOT NULL DEFAULT gen_random_uuid(),
    client_event_id  text        NULL,                     -- idempotency key from POST /select (SPEC-05)
    tipo             text        NOT NULL,                 -- 'SpeechNodeSelected' | 'PhraseMatched' | 'ConvServedDegraded'
    paciente_id      uuid        NOT NULL,
    no_id            uuid        NULL,
    payload          jsonb       NOT NULL,
    status           text        NOT NULL DEFAULT 'pendente', -- 'pendente' | 'enviado' | 'descartado'
    tentativas       integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL DEFAULT now(),
    sent_at          timestamptz NULL,
    PRIMARY KEY (id),
    CHECK (tipo IN ('SpeechNodeSelected','PhraseMatched','ConvServedDegraded')),
    CHECK (status IN ('pendente','enviado','descartado'))
);

CREATE INDEX ix_conv_outbox_status ON conv_audit_outbox (status, created_at);
CREATE UNIQUE INDEX uq_conv_outbox_client_event
    ON conv_audit_outbox (client_event_id) WHERE client_event_id IS NOT NULL;
COMMENT ON TABLE conv_audit_outbox IS 'Local retry buffer for HIST audit facts; idempotent by client_event_id (SPEC-05/09).';
```

**Invariants:**
- The partial UNIQUE index on `client_event_id` makes a retried `POST /select` with the same key a no-op insert (idempotency, SPEC-05).
- A row transitions `pendente → enviado` when HIST acknowledges; `pendente → descartado` only after the retry budget is exhausted (SPEC-09).

---

## 3. Elasticsearch projection — `neuroassist-ai_arvore-fala` (derived, read-only for CONV)

PostgreSQL (above) is **authoritative**. The ES index is a **derived projection written by TREI** via
`IndexPort` and **read by CONV** via `SearchPort` for semantic search. One document **per (node,
locale)** so each locale indexes/queries independently (`ADR-005`). Full mapping, kNN shape, and the
mandatory 8-field traceability block are the shared CONV↔TREI contract in
[`../design/index-design-arvore-fala.md`](../design/index-design-arvore-fala.md) (resolves gap G5).

Document shape (summary — authoritative mapping in index-design):

| Field | Type | Notes |
|---|---|---|
| `no_id` | keyword | also the doc `_id` component: `_id = "<no_id>_<locale>"` (deterministic, per-locale) |
| `arvore_id`, `paciente_id` | keyword | scoping filters |
| `tenant` | keyword | `empresa` id (the schema name) |
| `locale` | keyword | `pt` \| `en` |
| `tipo` | keyword | `categoria` \| `frase` (search filters `frase`) |
| `texto` | text (+`.keyword`) | display/spoken text of the locale |
| `embedding` | dense_vector | multilingual model (ADR-015); populated by TREI |
| `audio_ref` | keyword `index:false` | pointer, mirrors `conv_no_conteudo.audio_ref` |
| `status` | keyword | search filters `ativo` |
| 8× traceability block | per data-contract | **mandatory on every write** (D-269) — TREI must populate |

> **Consistency posture:** the ES index MAY lag PostgreSQL (TREI upsert failure/lag). CONV treats a
> miss as "not found" and degrades to static navigation (OPS-003) rather than assuming perfect sync;
> PostgreSQL remains the source of truth (risk R2, doc 06).

---

## 4. Relations (ASCII)

```
conv_arvore_fala (1) ──< (N) conv_no_arvore_fala ──< (N) conv_no_conteudo
       │  paciente_id (ref PAC)          │ parent_id (self, null=root)   (unique no_id+locale)
       │                                 └─ engine_feature_ref (opaque ref PAC/GEST)
       └─ conv_audit_outbox (paciente_id, no_id)   [CONV runtime writes only]

Derived projection (TREI writes, CONV reads):
   conv_no_conteudo (node,locale)  ──►  ES neuroassist-ai_arvore-fala  (_id = no_id_locale)
```

---

## 5. Migration plan (Flyway, shipped to MIG)

| Version | Script | Scope |
|---|---|---|
| `V0001` | `V0001__conv_extensions.sql` | global — `pgcrypto` (§0) |
| `V0002` | `V0002__conv_create_tree_tables.sql` | empresa — `conv_arvore_fala`, `conv_no_arvore_fala`, `conv_no_conteudo` (§2.1–2.3) |
| `V0003` | `V0003__conv_create_audit_outbox.sql` | empresa — `conv_audit_outbox` (§2.4) |

New per-tenant schemas get the empresa-scoped scripts provisioned by MIG like every other service. The
ES mapping is **not** a Flyway migration — it is created operationally from
`design/index-design-arvore-fala.md` alongside TREI's indexing pipeline (doc 05).

## 6. Schema-level mapping

| Table | Schema |
|---|---|
| `pgcrypto` extension | global (`neuroassist_global`) |
| `conv_arvore_fala` | empresa (`t_<empresa>`) |
| `conv_no_arvore_fala` | empresa |
| `conv_no_conteudo` | empresa |
| `conv_audit_outbox` | empresa |

## 7. Idempotency & concurrency (summary; full in SPEC-05)

- **Reads** (`/tree`, `/search`, `/bindings/resolve`) are naturally idempotent.
- **`POST /select`** is non-mutating on tree state; its only side effect (the HIST audit fact) is made
  idempotent by `client_event_id` via `uq_conv_outbox_client_event`.
- **Concurrency:** CONV runtime does not mutate tree/content rows, so there is no write-write contention
  on the tree; TREI's writes and CONV's reads are decoupled by the derived-projection posture (§3).
