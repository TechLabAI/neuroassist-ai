<!-- OUTPUT-SCHEMA
- Schema Strategy
- Initial Entities
- Migration Strategy
- Data Lifecycle
-->

# 05 — Persistence Model

## Schema Strategy
Schema-per-tenant (empresa), consistent with the shared multi-tenancy ADR and the rest of the monorepo:
- **`neuroassist_global`** — no CONV-owned global data in the MVP (the patient-with-session actor and its
  session live in ACS, not CONV — ADR-012).
- **`t_<empresa>` (per-tenant schema)** — the CONV tables (`conv_arvore_fala`, `conv_no_arvore_fala`,
  `conv_no_conteudo`) live here; every query is tenant-scoped by construction. Within a tenant, rows are
  further scoped by `paciente_id` (referenced by id only — PAC remains the system of record for
  `Paciente`).

CONV owns **two persistence surfaces that must stay consistent**: the relational tree (PostgreSQL, source
of truth and static-navigation fallback) and the Elasticsearch index `arvore-fala` (derived, for semantic
retrieval). PostgreSQL is authoritative; the ES index is a derived projection **written by TREI** on
approval/edit (incremental per-node upsert, ADR-015). CONV reads both and must tolerate the index lagging
or being unavailable (OPS-003). The cached audio lives outside the database entirely — a filesystem store,
referenced by path (ADR-017).

## Initial Entities
Draft tables (schema-per-tenant). Final DDL is produced by specs + MIG, not here.

- **`conv_arvore_fala`** — one row per patient tree. `id` (PK), `paciente_id` (ref PAC, not FK across
  service), `locales_suportados`, audit columns. Tenant is the schema itself.
- **`conv_no_arvore_fala`** — the tree nodes. `id` (PK), `arvore_id` (FK), `parent_id` (nullable self-FK,
  null = root), `tipo` (`categoria` | `frase`), `engine_feature_ref` (nullable, the shared vocabulary
  token), `ordem` (sibling order), audit columns. **Structure is locale-invariant** (ADR-018).
- **`conv_no_conteudo`** — per-locale content, **normalized** rather than a JSON blob so each locale can be
  activated/queried independently (ADR-018 leaves JSON-column vs normalized-table open; this draft favors
  the normalized table — final call in specs). `id` (PK), `no_id` (FK), `locale` (`pt` | `en`), `texto`,
  `audio_ref` (nullable path/uri + formato), `confirmado_cuidadora`, `confirmado_paciente`, `status`
  (`ativo` | `pendente`). Unique on (`no_id`, `locale`).

Elasticsearch `arvore-fala` document (class **dimension** — bounded by a single patient's curated tree,
1 primary shard, no ILM), **written by TREI**, read by CONV: `no_id` (doc `_id`), `arvore_id`,
`paciente_id`, `tenant`, `locale`, `texto`, `embedding` (`dense_vector`, multilingual model per ADR-015),
`audio_ref`, `tipo`, plus the mandatory 8-field traceability block (D-269). One document per (node, locale)
so each locale indexes independently (ADR-018 §3).

## Migration Strategy
Centralized **Flyway** via the `neuroassist-ai-migrations` (MIG) service (shared strategy) — CONV ships
its migration scripts to MIG; it does **not** run a local migrator. New per-tenant schemas get the CONV
tables provisioned by MIG like every other service. The Elasticsearch mapping for `arvore-fala`
(dimension class, `dense_vector` field for kNN) is an index-design artifact under
`docs/architecture/backend/services/conv-service/design/index-design.md` (to be produced in specs) and is
created/owned operationally alongside TREI's indexing pipeline — not by a Flyway migration.

## Data Lifecycle
- **Tree + content (PostgreSQL):** long-lived, low-volume (one patient, dozens of nodes). No archival/ILM
  needed at pilot scale. Edits are versioned through TREI's approval flow, not CONV.
- **Elasticsearch index:** derived; can be rebuilt from PostgreSQL at any time (TREI re-indexes per node).
  No ILM (dimension class) — a `arvore-fala` index growing past ~100k docs would signal a data-model error
  (per the ES integration contract), implausible at pilot scale.
- **Audio files (filesystem):** written once per (phrase, locale) by TREI; read many by CONV. Retention
  follows the node's lifecycle — when a node/locale is withdrawn, its audio becomes eligible for cleanup
  (a TREI-side operation; CONV never deletes).
- **Sensitivity (LGPD, SEC-002):** tree text and (upstream) cloned-voice audio are sensitive
  health/biometric data — same safeguards as REG-001/OPS-002. No biometric-capture/image/landmark data is
  ever persisted by CONV.
- **Consistency posture:** PostgreSQL is authoritative; the ES index and the audio store are derived and
  may lag or be absent — CONV's read paths degrade (OPS-003/OPS-004) rather than assume perfect sync.
