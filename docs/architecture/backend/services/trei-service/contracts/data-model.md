# TREI — Data Model (`trei-service`)

> Module **TREI** · Service `trei-service` · Java 21 / Spring Boot · PostgreSQL, schema-per-tenant (`empresa`).
> Canonical DDL lives in the executable ```` ```sql ```` blocks below — consumed by
> `be-migration-generator.py` (Flyway `V…__*.sql`) and `be-entity-generator.py` (JPA), shipped to the
> centralized **MIG** service (`neuroassist-ai-migrations`); TREI runs no local migrator (doc 05).
> Multi-tenancy per shared `ADR-003-multi-tenancy.md`. Bilingual model per `ADR-005` (→ ADR-018).
> Hexagonal boundary per the shared hexagonal ADR (`docs/architecture/backend/adr/ADR-001-hexagonal-architecture.md`).
> EARS acceptance criteria per [`../../../standards/ears-notation.md`](../../../standards/ears-notation.md) (D-057).

---

## 0. Ownership map — TREI writes, does not duplicate

TREI is the **authoring plane** and the **sole writer** of the speech-tree content that CONV reads
(doc 01, doc 04, doc 05). It has two kinds of persistent state:

| Surface | Tables | Owner of the **DDL** | TREI's role |
|---|---|---|---|
| Shared tree (published projection in PostgreSQL) | `conv_arvore_fala`, `conv_no_arvore_fala`, `conv_no_conteudo` | **CONV** ([`../../conv-service/contracts/data-model.md`](../../conv-service/contracts/data-model.md) §2) | **writes** (upsert nodes + per-locale content, flips approval flags and `status`) |
| Authoring process state (TREI-private) | `trei_sessao_treinamento`, `trei_proposta_llm`, `trei_conteudo_autoria`, `trei_publicacao_outbox` | **TREI** (this document §2) | owns |
| Derived search index (ES) | `neuroassist-ai_arvore-fala` | shared contract ([`../../conv-service/design/index-design-arvore-fala.md`](../../conv-service/design/index-design-arvore-fala.md)) | **writes** via `IndexPort` |
| Derived audio store (filesystem) | `${TREI_AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>` | shared convention (ADR-004 → ADR-017) | **writes** via `AudioStorePort` |

**Non-duplication rule (specs decision).** The tree tables (`conv_*`) are declared **once**, by CONV,
and shipped to MIG by CONV's migration set. TREI **does not re-declare** them — it writes into the same
tenant schema through JPA. This document declares DDL **only** for the four TREI-private tables. Any
change to a `conv_*` column TREI depends on (the approval flags, `status`, `audio_ref`) is a
**coordinated** change with CONV, never unilateral.

**What TREI writes on the shared tables (columns it is authoritative for):**

- `conv_arvore_fala` — creates the one-per-patient tree row on first authoring (`paciente_id`,
  `locales_suportados`).
- `conv_no_arvore_fala` — inserts nodes (`parent_id`, `tipo`, `engine_feature_ref`, `ordem`).
- `conv_no_conteudo` — inserts/edits per-locale content (`texto`, `audio_ref`, `audio_formato`),
  flips `confirmado_cuidadora` / `confirmado_paciente`, and transitions `status` `pendente → ativo`
  **only** after the publication pipeline (§4, ADR-004) completes. CONV never writes these columns
  (it reads `status = 'ativo'`).

---

## 1. Conventions

Inherited from the project + CONV's conventions (single schema-per-tenant boundary, so the two services
agree):

- **PK strategy:** `uuid` primary keys (`gen_random_uuid()` via `pgcrypto`, already provisioned by
  CONV §0). No natural keys as PK.
- **Timestamps:** `criado_em` / `atualizado_em` `timestamptz NOT NULL DEFAULT now()`; UTC.
- **Tenancy:** every TREI table lives in the **per-tenant schema** (`t_<empresa>`); the schema *is* the
  tenant boundary, so tables carry no `tenant_id` column. Rows are further scoped by `paciente_id`.
- **Cross-service references:** `paciente_id` references PAC's `Paciente` **by id only** (no cross-service
  FK, `ADR-013`); `cuidadora_id` / `paciente_sessao_sub` reference ACS identities **by id only**;
  `no_id` references `conv_no_arvore_fala.id` **within the same schema** (a real FK is allowed because it
  is the same database schema, but is declared `ON DELETE CASCADE` mirroring the tree).
- **Locale codes:** `pt` | `en` (2-letter, matching the ES `locale` field and `conv_no_conteudo.locale`).
- **Encoding/collation:** UTF-8; `texto`/transcript columns are plain `text`; no clinical text is stored
  in third-party-bound columns (SEC-002).
- **No binaries in PostgreSQL:** no audio blob and no embedding vector is stored here — only **references**
  (`audio_ref`, `embedding_ref`) and the **`embedding_model_id`** string (G5, §3). The vector lives in
  Elasticsearch; the audio in the filesystem.

---

## 2. TREI-private entities

### 2.1 `trei_sessao_treinamento` [empresa]

One LLM-assisted authoring session, caregiver-initiated (doc 01 persona; ADR-016). Scopes a set of LLM
proposals to one patient + tenant + authoring locale. The LLM conversation transcript is stored **by
reference** (`llm_transcript_ref`), never inline, to keep sensitive prose out of the relational row
(SEC-002).

```sql
-- schema: empresa
CREATE TABLE trei_sessao_treinamento (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    paciente_id         uuid        NOT NULL,               -- ref PAC.Paciente (no cross-service FK)
    cuidadora_id        uuid        NOT NULL,               -- ref ACS caregiver identity (by id)
    locale_autoria      text        NOT NULL,               -- 'pt' | 'en' (ADR-018: one authoring locale per session)
    estado              text        NOT NULL DEFAULT 'ativa',-- 'ativa' | 'encerrada'
    llm_model_id        text        NOT NULL,               -- Anthropic model id/version used (audit, TECH-007)
    llm_transcript_ref  text        NULL,                   -- pointer to transcript store; null until first turn
    iniciada_em         timestamptz NOT NULL DEFAULT now(),
    encerrada_em        timestamptz NULL,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale_autoria IN ('pt','en')),
    CHECK (estado IN ('ativa','encerrada')),
    CHECK (encerrada_em IS NULL OR estado = 'encerrada')
);

CREATE INDEX ix_trei_sessao_paciente ON trei_sessao_treinamento (paciente_id);
CREATE INDEX ix_trei_sessao_estado   ON trei_sessao_treinamento (estado, iniciada_em);
COMMENT ON TABLE trei_sessao_treinamento IS 'LLM authoring session (caregiver-initiated); one authoring locale (ADR-018).';
```

**Invariants:**
- `locale_autoria ∈ {pt,en}` and is fixed for the session (ADR-018: each locale authored independently).
- IF `estado = 'encerrada'`, THEN `encerrada_em` SHALL be non-null.
- THE SYSTEM SHALL record `llm_model_id` on session creation so every proposal is traceable to a concrete
  Anthropic model version (TECH-007).

### 2.2 `trei_proposta_llm` [empresa]

Traceability of what the LLM proposed (SEC-002). One row per LLM proposal turn that yields a candidate
node/content. The prompt and response are stored **by reference** (`prompt_ref`, `response_ref`) — never
inline — because they may echo sensitive everyday-need text.

```sql
-- schema: empresa
CREATE TABLE trei_proposta_llm (
    id             uuid        NOT NULL DEFAULT gen_random_uuid(),
    sessao_id      uuid        NOT NULL,
    no_id          uuid        NULL,                        -- set once the proposal is materialized as a draft node
    locale         text        NOT NULL,                    -- 'pt' | 'en' (== sessao.locale_autoria)
    texto_proposto text        NOT NULL,                    -- the phrase/category text the human will review
    prompt_ref     text        NULL,                        -- pointer to the prompt sent to Anthropic
    response_ref   text        NULL,                        -- pointer to the raw LLM response
    estado         text        NOT NULL DEFAULT 'proposta', -- 'proposta' | 'materializada' | 'descartada'
    criado_em      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale IN ('pt','en')),
    CHECK (estado IN ('proposta','materializada','descartada')),
    FOREIGN KEY (sessao_id) REFERENCES trei_sessao_treinamento (id) ON DELETE CASCADE,
    FOREIGN KEY (no_id)     REFERENCES conv_no_arvore_fala (id)     ON DELETE SET NULL
);

CREATE INDEX ix_trei_proposta_sessao ON trei_proposta_llm (sessao_id);
CREATE INDEX ix_trei_proposta_no     ON trei_proposta_llm (no_id) WHERE no_id IS NOT NULL;
COMMENT ON TABLE trei_proposta_llm IS 'LLM proposal traceability (SEC-002); prompt/response by reference.';
```

**Invariants:**
- THE SYSTEM SHALL persist one `trei_proposta_llm` row for every LLM-generated candidate before a human
  ever sees it, so no published node lacks an authorship trail (SEC-002, TECH-007).
- WHEN a proposal is turned into a draft node, THE SYSTEM SHALL set `no_id` and `estado = 'materializada'`.
- THE SYSTEM SHALL NOT store clinical/diagnostic text in `texto_proposto`; the authoring register stays in
  everyday-need vocabulary (SEC-002; ElevenLabs PHI caveat, ADR-017).

### 2.3 `trei_conteudo_autoria` [empresa] — per-(node, locale) authoring lifecycle & G5 record

The authoring-side companion of the shared `conv_no_conteudo` row. It holds the finer publication
sub-state that CONV does not model (`conv_no_conteudo.status` is only `ativo|pendente`), the outbox
linkage, and — critically — **the `embedding_model_id` that produced this locale's vector (G5, §3)**.
One row per `(no_id, locale)`, mirroring `conv_no_conteudo`'s uniqueness.

```sql
-- schema: empresa
CREATE TABLE trei_conteudo_autoria (
    id                  uuid        NOT NULL DEFAULT gen_random_uuid(),
    no_id               uuid        NOT NULL,               -- FK conv_no_arvore_fala (same schema)
    locale              text        NOT NULL,               -- 'pt' | 'en'
    sessao_id           uuid        NULL,                   -- authoring session that produced it
    proposta_id         uuid        NULL,                   -- originating LLM proposal
    sub_estado          text        NOT NULL DEFAULT 'rascunho',
        -- 'rascunho' | 'aguardando_aprovacao' | 'publicando' | 'ativo' | 'descartado'
    embedding_model_id  text        NULL,                   -- G5: model id that produced the indexed vector; null until embedded
    embedding_ref       text        NULL,                   -- pointer/doc-id of the ES document (no_id_locale)
    audio_ref           text        NULL,                   -- filesystem path (relative to TREI_AUDIO_ROOT)
    audio_formato       text        NULL,                   -- 'mp3' | 'opus'
    publicado_em        timestamptz NULL,
    criado_em           timestamptz NOT NULL DEFAULT now(),
    atualizado_em       timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale IN ('pt','en')),
    CHECK (sub_estado IN ('rascunho','aguardando_aprovacao','publicando','ativo','descartado')),
    CHECK (audio_formato IS NULL OR audio_formato IN ('mp3','opus')),
    CHECK (audio_ref IS NULL OR audio_formato IS NOT NULL),          -- a ref must declare its format
    CHECK (sub_estado <> 'ativo' OR (embedding_model_id IS NOT NULL AND embedding_ref IS NOT NULL)),
        -- G5 hard gate: a locale cannot be 'ativo' without a recorded embedding model id + ref
    FOREIGN KEY (no_id)       REFERENCES conv_no_arvore_fala (id)        ON DELETE CASCADE,
    FOREIGN KEY (sessao_id)   REFERENCES trei_sessao_treinamento (id)    ON DELETE SET NULL,
    FOREIGN KEY (proposta_id) REFERENCES trei_proposta_llm (id)          ON DELETE SET NULL,
    UNIQUE (no_id, locale)
);

CREATE INDEX ix_trei_autoria_no       ON trei_conteudo_autoria (no_id);
CREATE INDEX ix_trei_autoria_estado   ON trei_conteudo_autoria (sub_estado, atualizado_em);
CREATE INDEX ix_trei_autoria_model    ON trei_conteudo_autoria (embedding_model_id) WHERE embedding_model_id IS NOT NULL;
COMMENT ON TABLE trei_conteudo_autoria IS 'Per (node,locale) authoring lifecycle + G5 embedding_model_id record; mirrors conv_no_conteudo UNIQUE(no_id,locale).';
```

**Invariants:**
- UNIQUE `(no_id, locale)` — at most one authoring record per node per locale (mirrors `conv_no_conteudo`).
- **G5 gate (DDL-enforced):** IF `sub_estado = 'ativo'`, THEN `embedding_model_id` AND `embedding_ref`
  SHALL both be non-null — a locale never reaches `ativo` without a recorded index-time model id (§3).
- WHILE `sub_estado ∈ {rascunho, aguardando_aprovacao}`, THE SYSTEM SHALL keep the shared
  `conv_no_conteudo.status = 'pendente'` (CONV must not serve it).
- WHEN the publication pipeline completes, THE SYSTEM SHALL set `sub_estado = 'ativo'`, `publicado_em`,
  and flip `conv_no_conteudo.status = 'ativo'` in the same transaction boundary that closes the outbox.

### 2.4 `trei_publicacao_outbox` [empresa]

Drives the retryable publication pipeline **without a broker** (`messaging.enabled = false`, ADR-004).
One row per `(no_id, locale, passo)`; the pipeline advances `embed → tts → index` and marks the locale
`ativo` only when all three are `ok` (so CONV never observes a partial publication).

```sql
-- schema: empresa
CREATE TABLE trei_publicacao_outbox (
    id             uuid        NOT NULL DEFAULT gen_random_uuid(),
    no_id          uuid        NOT NULL,
    locale         text        NOT NULL,                    -- 'pt' | 'en'
    passo          text        NOT NULL,                    -- 'embed' | 'tts' | 'index'
    estado         text        NOT NULL DEFAULT 'pendente', -- 'pendente' | 'ok' | 'falha'
    tentativas     integer     NOT NULL DEFAULT 0,
    proxima_em     timestamptz NULL,                        -- next retry time (backoff)
    ultimo_erro    text        NULL,
    criado_em      timestamptz NOT NULL DEFAULT now(),
    atualizado_em  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id),
    CHECK (locale IN ('pt','en')),
    CHECK (passo IN ('embed','tts','index')),
    CHECK (estado IN ('pendente','ok','falha')),
    FOREIGN KEY (no_id) REFERENCES conv_no_arvore_fala (id) ON DELETE CASCADE,
    UNIQUE (no_id, locale, passo)                            -- one row per step; idempotent replay
);

CREATE INDEX ix_trei_outbox_due ON trei_publicacao_outbox (estado, proxima_em);
COMMENT ON TABLE trei_publicacao_outbox IS 'Broker-less retry buffer for the publication pipeline (ADR-004); step-idempotent by (no_id,locale,passo).';
```

**Invariants:**
- The UNIQUE `(no_id, locale, passo)` makes a retried step an idempotent no-op once `ok` (SPEC-05).
- WHILE any step for a `(no_id, locale)` is not `ok`, THE SYSTEM SHALL keep the locale in `publicando` and
  SHALL NOT mark it `ativo` (atomic publication, ADR-004).
- IF a step's `tentativas` exceeds the configured budget, THEN THE SYSTEM SHALL set `estado = 'falha'`,
  leave the locale in `publicando`, and surface it for operator/DASH attention (never silently `ativo`).

---

## 3. G5 — Embedding model-id parity invariant (CONV↔TREI) — **fixed here**

> This section discharges the handoff warning: *"the G5 invariant (embedding model-id parity between
> index-time in TREI and query-time in CONV) must be fixed explicitly in contracts/data-model.md."*

### 3.1 Statement

TREI computes the document embedding at **index time**; CONV builds the query vector at **query time**
via ES `query_vector_builder.text_embedding` ([CONV integration-contracts §1.1](../../conv-service/contracts/integration-contracts.md)).
kNN recall is only correct when **both sides use the same embedding model**. A mismatch does **not**
error — it silently degrades recall (CONV returns worse/empty matches), which in an assistive-health
context means the patient cannot find the phrase they need. G5 is therefore a **hard operational
invariant**, not a tuning knob.

### 3.2 The two model ids and their binding

| Side | When | Configured by | Value |
|---|---|---|---|
| **TREI (index-time)** | on `embed` step of the publication pipeline | env `TREI_EMBED_MODEL_ID` | id of the self-hosted multilingual sentence-transformers model (ADR-015) |
| **CONV (query-time)** | on every `POST /search` | env `CONV_EMBED_MODEL_ID` ([CONV integration-contracts §4](../../conv-service/contracts/integration-contracts.md)) | ES inference `model_id` used in `query_vector_builder` |

**Binding rule:** `TREI_EMBED_MODEL_ID` **MUST equal** `CONV_EMBED_MODEL_ID`, and both **MUST equal** the
ES inference endpoint/model id deployed for the `neuroassist-ai_arvore-fala` index. This single string is
the G5 anchor.

### 3.3 Mechanical enforcement (three layers, MVP)

1. **Dimension guard (mapping-level, already in place).** The shared index mapping fixes
   `embedding: dense_vector, dims: 768, similarity: cosine` with `dynamic: strict`
   ([index-design §6](../../conv-service/design/index-design-arvore-fala.md)). TREI SHALL produce vectors
   of **exactly** 768 dims, L2-normalized (cosine); a wrong-dimension write is rejected by ES.
2. **Recorded model id (PostgreSQL, TREI-owned).** TREI writes the model id that produced each vector into
   `trei_conteudo_autoria.embedding_model_id`, and the DDL CHECK in §2.3 forbids `sub_estado = 'ativo'`
   without it. This makes drift auditable per node/locale.
3. **Config-parity self-check (startup + publish).** TREI SHALL read `TREI_EMBED_MODEL_ID` and refuse to
   run the `embed` step (fail-closed, outbox `falha`) if the embedding sidecar reports a **runtime model
   id different from** `TREI_EMBED_MODEL_ID`. Deployment SHALL assert `TREI_EMBED_MODEL_ID ==
   CONV_EMBED_MODEL_ID` in the environment (documented in [`setup-local.md`](../setup-local.md) and the
   deploy manifest); a smoke check compares both env values at boot.

> **Coordinated follow-up (not unilateral).** Adding an explicit `embedding_model_id` **keyword field to
> the ES document** would make parity checkable at query time too. Because the mapping is `dynamic:
> strict` and **owned jointly with CONV**, this is proposed as a coordinated mapping change (tracked in
> backlog / index-design), **not** written unilaterally by TREI. For the MVP, the three layers above are
> the binding guard.

### 3.4 Model change = full re-embed + coordinated cutover (EARS)

- IF `TREI_EMBED_MODEL_ID` changes, THEN THE SYSTEM SHALL re-embed the **entire** `arvore-fala` corpus
  with the new model into a new index/alias generation, and CONV SHALL switch `CONV_EMBED_MODEL_ID` to the
  same value **only after** the reindex completes — no partial-corpus mixed-model state is ever served
  (ADR-004; index-design §11).
- THE SYSTEM SHALL treat a `model_id` mismatch as a **configuration error surfaced at deploy/boot**, never
  as a silent runtime state (mirrors CONV integration-contracts §1.1 EARS).
- WHEN TREI publishes a locale, THE SYSTEM SHALL record `embedding_model_id = TREI_EMBED_MODEL_ID` on the
  `trei_conteudo_autoria` row, so 100% of `ativo` locales carry the model id that produced them (doc 01
  "model-id coherence" success signal).

---

## 4. Publication state machine (authoring → ativo)

The dual-authorization gate (ADR-003) and the publication pipeline (ADR-004) as persisted state:

```
proposta (LLM)          -> trei_proposta_llm.estado = 'proposta'
  |
  v  materialize draft node + content row
rascunho                -> conv_no_arvore_fala row + conv_no_conteudo(status='pendente',
                           confirmado_cuidadora=false, confirmado_paciente=false)
                           trei_conteudo_autoria.sub_estado = 'rascunho'
  |
  v  caregiver approves (treinamento.no.aprovar.cuidadora)  -> confirmado_cuidadora = true
  |  patient  approves (treinamento.no.aprovar.paciente)    -> confirmado_paciente  = true
  |  (asynchronous, different shells, different identities — ADR-003)
  v  WHEN both flags true  => gate fires
aguardando_aprovacao -> publicando   (trei_conteudo_autoria.sub_estado; outbox rows created: embed,tts,index)
  |
  v  outbox: embed OK -> tts OK -> index OK  (retryable, no broker — ADR-004)
ativo                   -> trei_conteudo_autoria.sub_estado='ativo', embedding_model_id set (G5),
                           conv_no_conteudo.status='ativo'  => CONV can now retrieve/serve it
```

**Discard branch:** a `rascunho`/`aguardando_aprovacao` node may be discarded manually (ORG-001) →
`trei_conteudo_autoria.sub_estado='descartado'`; the `conv_no_conteudo` row (if any) stays `pendente` and
is never served. No automatic expiry.

**Edit branch:** editing an `ativo` phrase opens a **new** approval cycle for that `(no_id, locale)` while
the prior `conv_no_conteudo` stays `ativo` (no CONV availability gap, doc 05); only on re-publication does
the new content + embedding replace it (re-embed with the current `TREI_EMBED_MODEL_ID`, preserving G5).

**EARS**
- IF either approval flag is `false`, THEN THE SYSTEM SHALL NOT create any outbox row and SHALL NOT call
  the embedding sidecar, ElevenLabs, or ES (gate is the domain invariant, ADR-003).
- WHEN both `confirmado_cuidadora` AND `confirmado_paciente` are `true` for a `(no_id, locale)`, THE SYSTEM
  SHALL create the three outbox steps and begin the pipeline exactly once (idempotent on replay).
- THE SYSTEM SHALL mark a locale `ativo` (and flip `conv_no_conteudo.status`) **only** after `embed`,
  `tts`, and `index` are all `ok`.

---

## 5. Relations (ASCII)

```
trei_sessao_treinamento (1) ──< (N) trei_proposta_llm ──(no_id, SET NULL)──► conv_no_arvore_fala
        │ paciente_id (ref PAC)                                                   │
        │ cuidadora_id (ref ACS)                                                  │ (CONV-owned tree; TREI writes)
        └──< (N) trei_conteudo_autoria (no_id, locale)  UNIQUE(no_id,locale) ─────┤
                     │ embedding_model_id (G5)                                     │
                     └── mirrors ──► conv_no_conteudo (no_id, locale)  [CONV-owned; TREI writes texto/flags/status]
        trei_publicacao_outbox (no_id, locale, passo)  [TREI runtime, broker-less pipeline]

Derived stores (TREI writes, CONV reads):
   conv_no_conteudo (node,locale) ─embed+tts+index─►  ES neuroassist-ai_arvore-fala (_id = no_id_locale)
                                                  └─►  FS ${TREI_AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>
```

---

## 6. Migration plan (Flyway, shipped to MIG)

TREI contributes migrations to the centralized **MIG** service; it runs no local Flyway (doc 05). Versions
are drawn from a **TREI-reserved band** to avoid collision with CONV's `V0001–V0003` and other services.
The `conv_*` tables are **not** re-created here (owned by CONV's migration set, §0).

| Version | Script | Scope |
|---|---|---|
| `V0201` | `V0201__trei_create_sessao_proposta.sql` | empresa — `trei_sessao_treinamento`, `trei_proposta_llm` (§2.1–2.2) |
| `V0202` | `V0202__trei_create_conteudo_autoria.sql` | empresa — `trei_conteudo_autoria` (§2.3, G5 CHECK) |
| `V0203` | `V0203__trei_create_publicacao_outbox.sql` | empresa — `trei_publicacao_outbox` (§2.4) |

> **MIG coordination note.** The exact reserved band is confirmed with the MIG owner at backlog time; the
> only hard rule is (a) globally-unique version numbers across all services and (b) TREI's scripts run
> **after** CONV's `conv_*` tables exist (FK dependency on `conv_no_arvore_fala`). The `arvore-fala` ES
> mapping is **not** a Flyway migration — it is created operationally from
> [`../../conv-service/design/index-design-arvore-fala.md`](../../conv-service/design/index-design-arvore-fala.md)
> alongside TREI's indexing pipeline (doc 05).

## 7. Schema-level mapping

| Table | Schema |
|---|---|
| `pgcrypto` extension | global (`neuroassist_global`) — provisioned by CONV §0 |
| `trei_sessao_treinamento` | empresa (`t_<empresa>`) |
| `trei_proposta_llm` | empresa |
| `trei_conteudo_autoria` | empresa |
| `trei_publicacao_outbox` | empresa |
| `conv_arvore_fala` / `conv_no_arvore_fala` / `conv_no_conteudo` | empresa — **CONV-owned DDL; TREI writes** |

## 8. Data lifecycle & LGPD (SEC-002)

- **Drafts (`rascunho`)** retained indefinitely, no auto-expiry (ORG-001); manual discard → `descartado`
  (soft, retained for audit).
- **LLM transcripts / proposals** are sensitive; stored by reference, subject to the project LGPD posture
  (`_extensions.compliance.lgpd = true`).
- **Patient erasure** SHALL cascade for a `paciente_id` across: TREI PostgreSQL rows, the `conv_*` rows,
  the ES documents (`no_id_locale`), and the audio files — a coordinated erasure with CONV (retention
  windows are an open compliance item, doc 06).
