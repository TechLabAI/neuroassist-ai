
# Elasticsearch Query Capability Contract

> **Project:** NeuroAssist AI
> **Index prefix:** `neuroassist-ai_`
> **Cluster version:** 9.4.1
> **D-299:** Fill one copy per index. Declare which query shapes the index supports — derived from its mapping.
> **D-300:** `semantic_hybrid` (retrievers / RRF / `semantic_text`) requires ES 8.16+ (retrievers GA) / 9.x for ES|QL semantic.

---

## Artifact Identity

```yaml
artifact_name:     query-capability-<INDEX_SLUG>
artifact_category: design
artifact_role:     Sanctioned query, aggregation, and retrieval shapes for one index
artifact_dependencies:
  - index-design-<INDEX_SLUG>.md   # mapping that determines capability (D-272)
  - data-contract.md               # traceability + field-type conventions (D-269)
artifact_consumers:
  - application service            # builds the Query DSL Map passed to SearchPort
  - rag-workflow-<WORKFLOW_SLUG>   # retrieval strategy per question_type (D-274)
  - es-troubleshooting-playbook    # guardrails for invalid queries (D-301)
```

---

## Execution Contract

Use this template **once per index**. It is the bridge between an index's mapping
(`index-design`) and the runtime queries that hit it through `SearchPort`. The port
takes raw Query DSL as `Map<String, Object>` by design (D-271) — it imposes **no**
guardrails. This contract is where the guardrails live: it declares which query
shapes are *sanctioned* for the index and which are *forbidden* given its field types.

**Prerequisite:** the index mapping (`index-design` §6) must be final. Capability is
**derived from field types** — see §7. Do not declare a capability the mapping cannot serve.

---

## Input Schema

```yaml
index_name:              # full name: neuroassist-ai_<slug>
question_types:          # list: which rag-workflow intents this index serves
  - numeric_aggregation  # ES-only aggregations, no source
  - factual_lookup       # exact / term-level retrieval
  - explanatory          # full-text + compound retrieval
  - geo_analytical       # geo queries + bucket aggs
  - semantic_hybrid      # vector / semantic_text + RRF fusion (D-300)
searchable_text_fields:  # list: fields mapped as `text` (full-text capable)
exact_match_fields:      # list: fields mapped as `keyword` (term/agg/sort capable)
vector_fields:           # list: `dense_vector` / `semantic_text` fields (or "none")
geo_fields:              # list: `geo_point` / `geo_shape` fields (or "none")
```

---

## Output Schema

```yaml
sanctioned_queries:      # map: query shape → which fields it may target
forbidden_queries:       # list: shapes that would error or be slow on this mapping
relevance_strategy:      # precision↔recall posture (operator, minimum_should_match, boosts)
aggregation_catalogue:   # list: agg shapes this index supports + the field each targets
retrieval_class:         # classic | semantic | hybrid  (D-300)
default_query_per_intent:# map: question_type → the canonical Query DSL sketch
```

---

## Generation Rules

1. **Capability follows mapping.** A query shape is sanctioned only if the target
   field's type supports it (§7). `text` → full-text; `keyword` → term-level / aggs /
   sort. Declaring otherwise is a contract violation and will surface as a runtime
   error (see `es-troubleshooting-playbook` — D-301).
2. **One leaf query targets one field family.** A bare `match` cannot span multiple
   fields — use `multi_match` or a `bool`. (Common error: "[match] query doesn't
   support multiple fields"; D-301.)
3. **Filter, don't match, for binary criteria.** Date ranges, status, tenant scoping
   go in `bool.filter` (no scoring, cacheable), never in `must` with a `match`.
4. **Aggregations need `size: 0`.** When the answer is purely an aggregation, set
   `size: 0` at the request root so hits are not fetched (D-301, error #7).
5. **Aggregate on `keyword`, never analyzed `text`.** Use the `.keyword` sub-field;
   never enable `fielddata` on `text`. `date_histogram` needs a real `date` field.
6. **Pick the precision↔recall posture explicitly** (§5). Default `match` is OR-logic
   (high recall, low precision); declare `operator: and` / `minimum_should_match` /
   `match_phrase` when the use case needs precision.
7. **Semantic/hybrid is opt-in and version-gated (D-300).** Only declare
   `retrieval_class: hybrid` when the mapping has a `dense_vector` or `semantic_text`
   field AND the cluster is ES 8.16+. Otherwise stay `classic`.
8. **The port stays untyped.** This catalogue is documentation + a build guardrail, not
   a code change. Callers still build a `Map<String,Object>` and pass it to
   `SearchPort.search()` (D-271). Retrievers are just a different top-level key.

---

## §1 — Intent → Query Class

Map each `question_type` this index serves to its canonical query class.

| question_type | Query class | Canonical shape | Scoring? |
|---|---|---|---|
| numeric_aggregation | aggregation | `size:0` + metric/bucket aggs | no |
| factual_lookup | term-level | `term` / `terms` / `ids`, or `match` with `operator:and` | optional |
| explanatory | full-text + compound | `multi_match` inside `bool` (must/should) | yes |
| geo_analytical | geo + aggregation | `geo_bounding_box` / `geo_distance` + `terms` bucket | filter only |
| semantic_hybrid | retriever (D-300) | `rrf` over `standard` + `knn` / `semantic` | fused (RRF) |

---

## §2 — Leaf Query Catalogue

**Full-text (analyzed `text` fields):**

| Shape | Use case | Precision↔Recall | Sketch |
|---|---|---|---|
| `match` | term presence, any order | high recall / low precision | `{"match": {"<text>": {"query": "..."}}}` |
| `match` + `operator: and` | all terms present | higher precision | `{"match": {"<text>": {"query": "...", "operator": "and"}}}` |
| `match` + `minimum_should_match` | N-of-M terms | tunable | `{"match": {"<text>": {"query": "...", "minimum_should_match": 3}}}` |
| `match_phrase` | exact order + adjacency | high precision / low recall | `{"match_phrase": {"<text>": "..."}}` |
| `multi_match` | one query, many fields | recall ↑ | `{"multi_match": {"query": "...", "fields": ["<a>", "<b>"]}}` |
| `multi_match` + `^boost` | weight a field | precision ↑ on boosted field | `{"multi_match": {"query": "...", "fields": ["<a>^2", "<b>"]}}` |
| `multi_match` + `type: phrase` | phrase across fields | precision ↑↑ | `{"multi_match": {"query": "...", "fields": ["<a>^2", "<b>"], "type": "phrase"}}` |

**Term-level (`keyword` / numeric / `date` fields — no analysis):**

| Shape | Use case | Sketch |
|---|---|---|
| `term` | exact single value | `{"term": {"<keyword>": "VALUE"}}` |
| `terms` | exact any-of | `{"terms": {"<keyword>": ["A", "B"]}}` |
| `range` | numeric / date window | `{"range": {"<date>": {"gte": "...", "lte": "..."}}}` |
| `exists` | field present | `{"exists": {"field": "<f>"}}` |
| `prefix` / `wildcard` | partial keyword (costly) | `{"prefix": {"<keyword>": "abc"}}` |

---

## §3 — Compound Queries (`bool`)

The four `bool` clauses and their effect on hits and score:

| Clause | Effect on result set | Effect on score | Typical use |
|---|---|---|---|
| `must` | narrows (AND) | contributes | required matching criteria |
| `must_not` | removes | none | exclusions |
| `should` | does **not** add/remove (when a `must` exists) | boosts ranking | "nice to have" relevance signals |
| `filter` | narrows (AND), binary yes/no | **none** (cacheable) | dates, status, tenant scope |

```json
{
  "query": {
    "bool": {
      "must":     [ { "multi_match": { "query": "...", "fields": ["<a>^2", "<b>"] } } ],
      "should":   [ { "match": { "<a>": "..." } } ],
      "must_not": [ { "term":  { "<keyword>": "EXCLUDED" } } ],
      "filter":   [ { "range": { "<date>": { "gte": "...", "lte": "..." } } } ]
    }
  }
}
```

> Rule of thumb: more clauses in `must` → higher precision; `should` only reshuffles
> ranking; put everything non-scoring in `filter` so it is cached and cheap.

---

## §4 — Aggregation Catalogue

Declare `size: 0` at the request root when only aggregations are needed.

**Metric (target a numeric or `keyword` field):**

| Agg | Answers | Sketch |
|---|---|---|
| `sum` / `avg` / `min` / `max` | totals, averages, extremes | `{"<name>": {"sum": {"field": "<num>"}}}` |
| `stats` | count+min+max+avg+sum in one | `{"<name>": {"stats": {"field": "<num>"}}}` |
| `cardinality` | distinct count | `{"<name>": {"cardinality": {"field": "<keyword>"}}}` |
| `value_count` | non-null occurrences | `{"<name>": {"value_count": {"field": "<f>"}}}` |

**Bucket:**

| Agg | Answers | Sketch |
|---|---|---|
| `terms` | top-N by frequency | `{"<name>": {"terms": {"field": "<keyword>", "size": 5}}}` |
| `range` | custom numeric tiers | `{"<name>": {"range": {"field": "<num>", "ranges": [{"to": 50}, {"from": 50}]}}}` |
| `histogram` | fixed numeric buckets | `{"<name>": {"histogram": {"field": "<num>", "interval": 10}}}` |
| `date_histogram` | time buckets | `{"<name>": {"date_histogram": {"field": "<date>", "calendar_interval": "1M"}}}` |
| `geohash_grid` | geo buckets | `{"<name>": {"geohash_grid": {"field": "<geo_point>"}}}` |

**Nested & scoped:**

- Sub-aggregations nest under an inner `"aggs"` key inside the parent bucket.
- Sort a bucket by a sub-metric via `"order": {"<sub_agg>": "desc"}`.
- A top-level `query` **scopes** the aggregation to the matching subset.
- `fixed_interval` keeps the interval constant; `calendar_interval` follows the calendar.

---

## §5 — Relevance & Precision↔Recall

The knobs this index exposes for tuning result quality (no algorithm change required):

| Knob | Pushes toward | Where |
|---|---|---|
| default `match` (OR) | recall | leaf full-text |
| `operator: and` | precision | `match` |
| `minimum_should_match: N` | tunable | `match` |
| `match_phrase` | precision | leaf full-text |
| field `^boost` | precision on key fields | `multi_match` |
| `should` clauses | ranking only | `bool` |
| `filter` clauses | neither (binary), faster | `bool` |

**Declared posture for this index:** _(fill in: e.g. "explanatory queries use
`multi_match` with `headline^2`; precision via `minimum_should_match: 2`")_

---

## §6 — Semantic & Hybrid Retrieval (D-300)

Only when the mapping has a `dense_vector` or `semantic_text` field **and** the cluster
is ES 8.16+. Retrievers compose multi-stage retrieval in a single `_search` call.

| Retriever | Role |
|---|---|
| `standard` | wraps any classic Query DSL (BM25 lexical) |
| `knn` | dense-vector / `semantic_text` similarity |
| `rrf` | Reciprocal Rank Fusion of multiple retrievers (rank-based, no score tuning) |
| `text_similarity_reranker` | semantic rerank as a final pass |

```json
{
  "retriever": {
    "rrf": {
      "retrievers": [
        { "standard": { "query": { "multi_match": { "query": "...", "fields": ["<text>"] } } } },
        { "knn": { "field": "<semantic_or_vector>", "query_vector_builder": { "text_embedding": { "model_text": "..." } }, "k": 50, "num_candidates": 100 } }
      ],
      "rank_constant": 60
    }
  }
}
```

> Hybrid (BM25 + vector fused by RRF) is the recommended default for `semantic_hybrid`
> intents — it outperforms either alone with almost no tuning (`rank_constant: 60`).
> RRF ignores raw scores and fuses by rank, sidestepping incompatible score ranges.

**Retrieval class for this index:** _(fill in: classic / semantic / hybrid)_

---

## §7 — Field-Type → Capability Matrix

The authoritative derivation rule. A capability that is not ✓ here is **forbidden** —
declaring it produces a runtime error (D-301).

| Field type | Full-text (`match`) | Exact (`term`) | Aggregations | Sort | Vector / semantic |
|---|---|---|---|---|---|
| `text` | ✓ | ✗ (analyzed) | ✗ (no `fielddata`) | ✗ | ✗ |
| `keyword` | ✗ | ✓ | ✓ | ✓ | ✗ |
| `text` + `.keyword` multi-field | ✓ (on `text`) | ✓ (on `.keyword`) | ✓ (on `.keyword`) | ✓ | ✗ |
| `long` / `double` / `integer` | ✗ | ✓ (`term`/`range`) | ✓ | ✓ | ✗ |
| `date` | ✗ | ✓ (`range`) | ✓ (`date_histogram`) | ✓ | ✗ |
| `boolean` | ✗ | ✓ | ✓ | ✓ | ✗ |
| `geo_point` | ✗ | geo queries | `geohash_grid` | ✗ | ✗ |
| `dense_vector` / `semantic_text` | via `semantic` | ✗ | ✗ | ✗ | ✓ |

> Mapping is immutable for existing fields — changing a type requires a new index +
> `_reindex` (D-301, error #8). Decide capability **before** creating the index.

---

## §8 — This Index's Declared Capabilities

_(Fill in, derived from the index mapping.)_

**Sanctioned queries:**
1. _(shape → field)_
2. _(shape → field)_

**Forbidden queries (and why):**
- _(e.g. "terms agg on `headline` — it is `text`; use `headline.keyword`")_

**Default query per intent:**

| question_type | Query DSL sketch |
|---|---|
| _(intent)_ | _(sketch)_ |

---

## Sources

- ES/Kibana workshop — relevance, full-text & combined queries, aggregations, mapping
  (Parts 2–5, Lisa Jung).
- Elastic reference — Retrievers, hybrid search with `semantic_text`, Query DSL (8.16+/9.x).
