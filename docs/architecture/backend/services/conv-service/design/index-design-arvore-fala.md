# Elasticsearch Index Design — `neuroassist-ai_arvore-fala`

> **Project:** NeuroAssist AI · **Index prefix:** `neuroassist-ai_` · **Cluster:** 9.4.1
> **Owner module (write):** TREI · **Reader:** CONV (`conv-service`) via `SearchPort`
> This is the **shared CONV↔TREI data contract** for the semantic-search index (resolves gap G5).
> Derived projection of the authoritative PostgreSQL tree ([`../contracts/data-model.md`](../contracts/data-model.md) §2/§3).

---

## §2 — Class: **dimension**

Bounded by one patient's curated tree (dozens of nodes × 2 locales). 1 primary shard, default refresh,
**no ILM**. A count exceeding ~100k documents signals a data-model error (implausible at pilot scale).

## §3 — Purpose

Serve CONV's **assisted semantic search** (`POST /api/v1/conv/search`): given a captured intent, return
the closest **already-authored** `frase` node in the session's active locale via kNN over a
`dense_vector`. Retrieval-only (`ADR-002`/ADR-015) — the index never holds generated text; it mirrors
`conv_no_conteudo` rows that are `ativo`.

## §4 — Source records

One source record per **(node, locale)** content row (`conv_no_conteudo`), written by **TREI** on
approval/edit (incremental per-node upsert). CONV never writes this index.

## §5 — Document granularity

**One document per (node, locale).** Deterministic `_id = "<no_id>_<locale>"` → idempotent upsert/replay
(data-contract §5). Each locale indexes and is queried independently (`ADR-005`).

## §6 — Mapping design

Includes the mandatory 8-field traceability block (data-contract §1, D-269) — enforced by
`ElasticsearchAdapter` on every write, so **TREI must populate it**. `dynamic: strict`.

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 0,
    "refresh_interval": "1s"
  },
  "mappings": {
    "dynamic": "strict",
    "properties": {
      "source_file_id":    { "type": "keyword" },
      "line_number":       { "type": "long" },
      "raw_line":          { "type": "text", "index": false },
      "register_code":     { "type": "keyword" },
      "block":             { "type": "keyword" },
      "parse_status":      { "type": "keyword" },
      "ingested_at":       { "type": "date" },
      "synthetic_dataset": { "type": "boolean" },

      "no_id":             { "type": "keyword" },
      "arvore_id":         { "type": "keyword" },
      "paciente_id":       { "type": "keyword" },
      "tenant":            { "type": "keyword" },
      "locale":            { "type": "keyword" },
      "tipo":              { "type": "keyword" },
      "status":            { "type": "keyword" },
      "engine_feature_ref":{ "type": "keyword" },
      "audio_ref":         { "type": "keyword", "index": false },
      "texto":             { "type": "text", "fields": { "keyword": { "type": "keyword" } } },
      "embedding":         { "type": "dense_vector", "dims": 768, "index": true, "similarity": "cosine" }
    }
  }
}
```

> `dims` is illustrative (768 = a common multilingual model width); the **authoritative value is the
> multilingual model TREI deploys (ADR-015)** and MUST match the ES inference `model_id` CONV uses for
> `query_vector_builder`. `similarity: cosine` pairs with normalized embeddings.

## §7 — Example document (per-locale, with traceability)

```json
{
  "source_file_id":    "trei-session-2026-08-31",
  "line_number":       42,
  "raw_line":          "I want water with ice",
  "register_code":     "FRASE",
  "block":             "CONV",
  "parse_status":      "ok",
  "ingested_at":       "2026-08-31T18:00:00Z",
  "synthetic_dataset": false,

  "no_id":             "n-42",
  "arvore_id":         "b1e2…",
  "paciente_id":       "p-1024",
  "tenant":            "clinica-acme",
  "locale":            "en",
  "tipo":              "frase",
  "status":            "ativo",
  "engine_feature_ref":"gest:blink-double",
  "audio_ref":         "clinica-acme/p-1024/en/n-42.opus",
  "texto":             "I want water with ice",
  "embedding":         [0.013, -0.221, "…768 floats…"]
}
```

**Traceability mapping guidance for TREI:** `source_file_id` = TREI authoring session id;
`register_code` = node `tipo` (`FRASE`/`CATEGORIA`); `block` = `"CONV"`; `parse_status` = `ok`;
`synthetic_dataset` = `false` for real content. CONV's read path does not require these fields, but the
shared `IndexPort` adapter rejects writes without all 8 (DLQ routing).

## §8 — Query patterns (CONV read)

1. **Assisted kNN** (primary): top-level `knn` with `query_vector_builder.text_embedding` (model_id),
   `filter` on `paciente_id` + `locale` + `status:ativo` + `tipo:frase`. See
   [`../contracts/integration-contracts.md`](../contracts/integration-contracts.md) §1.1.
2. **Existence/health probe:** `term` on `paciente_id` + `count` to confirm the index has this patient's
   nodes (readiness signal for semantic mode).
3. **Locale-scoped lexical fallback (optional):** `match` on `texto` filtered by `locale`+`status` — a
   BM25 assist if the vector half is unavailable but the index is up (kept simple at MVP; static PG walk
   is the primary fallback).

## §9 — Aggregation patterns

1. `terms` on `tipo` (counts of `categoria` vs `frase`) — sanity/coverage.
2. `terms` on `locale` filtered by `status:ativo` — per-locale authored coverage (feeds a "pending EN"
   signal surfaced by TREI/DASH, not CONV runtime).

## §10 — Performance notes

- **Shards:** 1 primary (dimension). **ILM:** none. **Replicas:** 0 at single-node pilot (OPS-002).
- **kNN:** `num_candidates` ≈ 10× `k` (e.g. k=5 → 50); HNSW defaults are ample at dozens of vectors.
- **High cardinality:** none at pilot scale; `texto.keyword` only for exact/agg, never `fielddata` on
  `texto`.

## §11 — Validation rules

- [ ] 100% of documents carry all 8 traceability fields (adapter-enforced; DLQ on violation).
- [ ] `_id == "<no_id>_<locale>"` for every doc (idempotent replay).
- [ ] Every `status:ativo` document has a non-empty `texto` and an `embedding` of the model's dims.
- [ ] `embedding` dims == CONV `CONV_EMBED_MODEL_ID` model dims (consistency, gap G5 / risk R5).
- [ ] Zero documents with `tipo:categoria` returned by the CONV kNN filter (search targets `frase`).
