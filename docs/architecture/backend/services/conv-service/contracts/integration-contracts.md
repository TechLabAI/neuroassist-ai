# CONV — Integration Contracts (external / infrastructure)

> Module **CONV** · External & infrastructure integrations. Internal platform services (ACS, PAC/GEST,
> HIST, GWY/REG) are in [`hub-integration.md`](hub-integration.md). No third-party API sits on the CONV
> runtime path (no LLM, no ElevenLabs — `ADR-002`/`ADR-004`).

---

## 1. Elasticsearch 9.4.1 — semantic retrieval (via `elastic-stack-client-lib`)

- **Index:** `neuroassist-ai_arvore-fala` (slug `arvore-fala`, class **dimension**). Declared in
  `project-context.json` (`stack.elastic.indices[]`). Mapping/kNN shape:
  [`../design/index-design-arvore-fala.md`](../design/index-design-arvore-fala.md).
- **Access:** **only** through the shared hexagonal port `SearchPort` from
  `integrations/elastic-stack/ports/SearchPort.java` (adapter `ElasticsearchAdapter` is the sole holder
  of `co.elastic.clients`). CONV imports the lib as a dependency; it **never** instantiates its own ES
  client (`ADR-002`, principle "reuse the platform's seams"). CONV uses **read** operations only
  (`SearchPort`); writes (`IndexPort`) are TREI's.
- **Direction:** CONV reads. TREI writes (embeddings, upserts).
- **Failure posture:** **soft dependency, fail-open** → static navigation from PostgreSQL, `degraded:
  true` (OPS-003). Transport/5xx errors surface as `ElasticException` (translated by the lib) and are
  caught in CONV's application layer, which switches to the static path.

### 1.1 kNN gap (G1) — RESOLVED, no port change required

**Finding.** `SearchPort.search(String indexPattern, Map<String,Object> query)` is documented as "Query
DSL as a plain map", but its adapter implementation does:

```java
SearchRequest.of(s -> s.index(indexPattern)
                       .withJson(new ByteArrayInputStream(objectMapper.writeValueAsBytes(query))))
```

`.withJson(...)` deserializes the map as the **entire `_search` request body**, *not* as the content of
a top-level `query` field. Therefore a caller can pass a body that carries a **top-level `knn` block
sibling to `query`/`filter`** and it is sent to ES verbatim. **kNN is expressible through the existing
`SearchPort` today** — no additive method is needed (confirms `ADR-002`'s open item; closes risk R1/gap
G1 without forking or extending the shared lib).

**Query vector at runtime (retrieval-only, no LLM).** CONV does **not** compute embeddings. The kNN
block uses ES-side `query_vector_builder.text_embedding` pointing to the **same multilingual embedding
model** (`model_id`) that TREI used to index (ADR-015). ES computes the query vector internally; CONV
only passes the intent text. This keeps "CONV runtime never calls an LLM/embedding service" true while
enabling semantic search.

**Sanctioned search body** (built by the application layer, passed to `SearchPort.search`):

```json
{
  "size": 5,
  "knn": {
    "field": "embedding",
    "k": 5,
    "num_candidates": 50,
    "query_vector_builder": {
      "text_embedding": { "model_id": "<multilingual-embed-model>", "model_text": "some water please" }
    },
    "filter": [
      { "term": { "paciente_id": "p-1024" } },
      { "term": { "locale": "en" } },
      { "term": { "status": "ativo" } },
      { "term": { "tipo": "frase" } }
    ]
  },
  "_source": ["no_id", "texto", "tipo", "engine_feature_ref", "audio_ref"]
}
```

**Shared operational invariant (CONV↔TREI, gap G5):** the `model_id` used here for the query vector
MUST equal the model TREI used to produce `embedding` at index time; a mismatch silently degrades recall
(risk R5). Recorded as a hard operational contract in the index-design doc.

**Documentation follow-up (non-blocking, for scaffold/backlog):** the `SearchPort.search` Javadoc says
"Query DSL as a plain map" while the adapter treats the map as the full search body. Recommend an
**additive Javadoc clarification** in `elastic-stack-client-lib` (no signature change) so future callers
know top-level `knn`/`retriever` keys are accepted. This is a doc-only change owned by the scaffold/lib,
not a CONV runtime change.

**EARS**

- WHEN assisted search runs and ES is reachable, THE SYSTEM SHALL call `SearchPort.search("neuroassist-ai_arvore-fala", body)` with a top-level `knn` block filtered by `paciente_id`, `locale`, `status='ativo'`, `tipo='frase'`.
- THE SYSTEM SHALL use the TREI-aligned `model_id` for `query_vector_builder`; the platform SHALL treat a `model_id` mismatch as a configuration error, not a silent runtime state.
- IF `SearchPort` raises `ElasticException`, THEN THE SYSTEM SHALL fall back to static navigation and set `degraded: true` (OPS-003).

---

## 2. Audio store — local filesystem (`AudioRefPort`)

- **Nature:** pre-synthesized voice files on the single server's **local filesystem** (`ADR-004` →
  ADR-017). No object storage in the MVP.
- **Access:** through `AudioRefPort` (infrastructure adapter): map a `(no_id, locale)` to its cached
  file and confirm presence. CONV **reads** by reference; **TREI writes** (ElevenLabs synthesis).
- **Layout & naming (resolves gap G6):**
  - Root: `${CONV_AUDIO_ROOT}` (env; e.g. `/var/neuroassist/audio`).
  - Path: `${CONV_AUDIO_ROOT}/<empresa>/<paciente_id>/<locale>/<no_id>.<ext>` where `<ext>` ∈
    `opus`|`mp3`. This path is the `conv_no_conteudo.audio_ref` value (relative to root), e.g.
    `clinica-acme/p-1024/en/n-42.opus`.
  - **Presence is the fallback signal:** file missing / `audio_ref` null / store unreachable ⇒
    `audioRef.present: false` ⇒ frontend uses Web Speech (OPS-004).
- **Failure posture:** **soft dependency, fail-open** → text returned with null audio ref,
  `playbackMode: web-speech`, `degraded: true`.
- **Sensitivity:** cloned-voice audio is sensitive/biometric data (SEC-002 / LGPD); the store inherits
  REG-001/OPS-002 safeguards. CONV never writes, never deletes audio.

**EARS**

- WHEN a node is selected and its `audio_ref` resolves to a present file, THE SYSTEM SHALL return the reference with `playbackMode: audio-cacheado`.
- IF the `audio_ref` is null or the file is absent/unreachable, THEN THE SYSTEM SHALL return the text with `audioRef.present: false`, `playbackMode: web-speech`, and `degraded: true` (OPS-004).

---

## 3. What CONV does NOT integrate with (boundary guards)

| System | Why not |
|---|---|
| ElevenLabs API | Synthesis is TREI-only; CONV plays cached audio (`ADR-004`). Runtime never calls it (TECH-008). |
| Any LLM / TechLab AI | `ai.hasAi = false`; retrieval-only runtime (`ADR-002`/ADR-015). No orchestration/knowledge/composition/analytics. |
| Object storage (S3/etc.) | MVP uses local filesystem (ADR-017); revisit only at institutional scale. |
| Async broker (Kafka/RabbitMQ) | `messaging.enabled = false`; all cross-service calls synchronous. Future decision only. |
| Biometric/image/landmark ingress | Rejected at the API boundary with `400` (SEC-002); no such data enters CONV. |

## 4. Configuration surface (env)

| Var | Purpose |
|---|---|
| `CONV_AUDIO_ROOT` | Filesystem root of the cached audio store (§2). |
| `CONV_ES_INDEX` | ES index name (default `neuroassist-ai_arvore-fala`). |
| `CONV_EMBED_MODEL_ID` | ES inference `model_id` for `query_vector_builder`; MUST match TREI's index-time model (§1.1). |
| `ELASTICSEARCH_*` | Provided by `elastic-stack-client-lib` config (`ElasticsearchClientConfig`). |
