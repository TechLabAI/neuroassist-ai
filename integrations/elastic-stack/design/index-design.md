
# Elasticsearch Index Design Contract

> **Project:** NeuroAssist AI
> **Index prefix:** `neuroassist-ai_`
> **Cluster version:** 9.4.1
> **D-272:** Fill one copy of this document per index. Classify dimension or fact first.

---

## Artifact Identity

```yaml
artifact_name:     index-design-<INDEX_SLUG>
artifact_category: design
artifact_role:     Elasticsearch index structure, mapping, and lifecycle policy
artifact_dependencies:
  - data-contract.md          # traceability block fields (D-269)
  - integration-contract.md   # index registration table
artifact_consumers:
  - ElasticsearchAdapter      # runtime
  - Kibana data view          # exploration
  - RAG workflow              # retrieval
```

---

## Execution Contract

Use this template **once per index** when designing a new Elasticsearch index for
`NeuroAssist AI`. Complete every section before creating the index in
any environment. The mapping JSON in §6 is the authoritative schema definition.

**Prerequisite:** Decide the index class (§2) before filling any other section.
The class determines the shard/ILM strategy and must not change after the index
is created without a full reindex.

---

## Input Schema

```yaml
index_name:              # full name: neuroassist-ai_<slug>
index_class:             # REQUIRED: "dimension" | "fact"  — see §2
domain_context:          # which module/bounded context owns this index
primary_queries:         # list: at least 3 query patterns this index must answer
primary_aggregations:    # list: at least 2 aggregation patterns
geo_requirements:        # boolean + geo field description (or "none")
rag_requirements:        # boolean + retrieval description (or "none")
volume_expectation:      # estimated document count at production scale
retention_expectation:   # how long documents must be kept (or "indefinite")
```

---

## Output Schema

```yaml
index_purpose:           # one paragraph: why this index exists, what questions it answers
source_records:          # list of source record types / event types feeding this index
document_granularity:    # one document per: [raw line | entity | event | chunk | ...]
field_groups:            # map: group name → list of field definitions
mapping_design:          # JSON: explicit ES mapping
example_document:        # JSON: one representative document with traceability block
query_patterns:          # list: query DSL sketches
aggregation_patterns:    # list: aggregation DSL sketches
performance_notes:       # shard count, ILM policy, bulk settings, refresh interval
validation_rules:        # post-ingestion checks
```

---

## Generation Rules

1. **Classify before sizing.** Determine dimension vs fact (§2) before specifying
   shard count, ILM, or bulk settings. This is the most important decision.

2. **Use `keyword` for all identifiers and codes.** Never rely on `text` analysis
   for fields used in filters, terms aggregations, or joins.

3. **Double-map names.** Fields that need both full-text search and aggregation get
   `"type": "text"` with a `"fields": {"keyword": {"type": "keyword"}}` sub-field.

4. **Numeric types matter.** Use `integer`/`long`/`double` explicitly. Avoid `float`
   for monetary or high-precision values.

5. **Preserve traceability.** Every mapping must include all 8 fields from the
   traceability block (data-contract.md — D-269). Map `raw_line` as
   `{"type": "text", "index": false}` to store without analysis overhead.

6. **Explicit mapping only.** Set `"dynamic": "strict"` on business-critical indices.
   Unknown fields must fail loudly, not silently land in dynamic fields.

7. **Geo fields.** Use `"type": "geo_point"` for `{lat, lon}` coordinates. Use
   `"type": "geo_shape"` only for polygon/line geometry. Kibana Maps requires
   `geo_point`; do not use `scaled_float` as a geo substitute.

8. **Do not hardcode domain-specific values** (e.g., specific register codes, entity
   names) in the mapping itself. Those belong in the example document and query
   patterns sections.

9. **Vector / semantic fields are opt-in (D-300).** Only add a `dense_vector` or
   `semantic_text` field when the index serves a `semantic_hybrid` RAG intent and the
   cluster is ES 8.16+ (retrievers GA). Declare the sanctioned retrieval shapes in the
   index's `query-capability` contract §6 (D-299); never enable hybrid on a mapping
   without a vector/`semantic_text` field.

---

## §2 — Dimensions vs Facts (D-272)

**Classify this index before proceeding.**

| Class | Definition | Scaling behavior | Shard strategy | ILM |
|---|---|---|---|---|
| **dimension** | Bounded by the company's universe: participants, catalog items, reference data. Size is determined by the number of unique entities, not by transaction volume. | Grows to a finite ceiling; does not scale linearly with data volume. | 1 primary shard. Default refresh interval. | None, or a long hot phase (months). Monitor if count unexpectedly exceeds 100k — that indicates a data model error. |
| **fact** | Transaction-level data: documents, line items, events, log records. Each processed record produces one or more facts. | Scales linearly with data volume. At high volume reaches tens of millions of documents and tens of GB per index. | Size shards for projected doc count (target: 10–50GB per shard). Consider index sorting on `register_code` / `uf`. | Rollover (hot → warm → cold) with configurable retention. Set `refresh_interval: -1` and `number_of_replicas: 0` during initial bulk load; restore after. |

**This index class:** _(fill in: dimension / fact)_

---

## §3 — Index Purpose

_(Fill in: one paragraph describing why this index exists and which user or system
queries it answers. Reference the owning module and the domain context.)_

---

## §4 — Source Records

_(Fill in: list the source record types, domain events, or file formats that feed
this index. Example: "SPED register C170 — goods document items".)_

---

## §5 — Document Granularity

Choose exactly one:

- [ ] One document per raw source line
- [ ] One document per unique entity (participant, item, facility)
- [ ] One document per transaction/fiscal document
- [ ] One document per transaction line item
- [ ] One document per knowledge chunk (for RAG indices)
- [ ] One document per validation finding
- [ ] Other: _(describe)_

---

## §6 — Mapping Design

Provide the complete explicit mapping. Include the traceability block and all
domain fields. Set `"dynamic": "strict"` for business-critical indices.

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
      "synthetic_dataset": { "type": "boolean" }
    }
  }
}
```

_(Extend the `properties` block with domain-specific fields above.)_

---

## §7 — Example Document

Provide one representative document that would be indexed. Must include all
8 traceability fields (D-269).

```json
{
  "source_file_id":    "example_source_001",
  "line_number":       1,
  "raw_line":          "example raw content",
  "register_code":     "EXAMPLE",
  "block":             "0",
  "parse_status":      "ok",
  "ingested_at":       "2026-01-15T10:00:00Z",
  "synthetic_dataset": true
}
```

---

## §8 — Query Patterns

List at least 3 query patterns this index must support. For each, sketch the
query DSL or describe the filter/aggregation combination.

1. _(pattern 1)_
2. _(pattern 2)_
3. _(pattern 3)_

---

## §9 — Aggregation Patterns

List at least 2 aggregation patterns (for dashboards, reports, or RAG context).

1. _(aggregation 1)_
2. _(aggregation 2)_

---

## §10 — Performance Notes

Address these points based on the index class decision in §2:

**Shard count:**
_(Justify. For dimension: 1 shard. For fact: target 10–50GB/shard at projected volume.)_

**ILM policy:**
_(For dimension: none or long hot phase. For fact: rollover + hot/warm + retention.)_

**Bulk load settings:**
_(For fact indices: set `refresh_interval: -1` and `replicas: 0` during initial load.
Restore to defaults after. Batch size: 1,000–5,000 docs per request.)_

**High-cardinality fields:**
_(List any fields that may have high cardinality and how they are handled — avoid
`fielddata: true` on `text` fields; use `.keyword` sub-field for aggregations.)_

---

## §11 — Validation Rules

Define post-ingestion checks (run after bulk load or as a scheduled probe):

- [ ] Document count matches expected count ± tolerance
- [ ] Zero documents with `parse_status: "error"` (or within acceptable threshold)
- [ ] All 8 traceability fields present on 100% of documents
- [ ] Geo fields populated for entity-type indices (if geo_requirements = true)
- [ ] _(add domain-specific checks)_

---

## Example Output (filled section §6)

```yaml
index_name:          neuroassist-ai_entities
index_class:         dimension
document_granularity: one document per unique entity
volume_expectation:  < 10,000 documents (bounded by company universe)
```

```json
{
  "settings": {
    "number_of_shards": 1,
    "number_of_replicas": 1,
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
      "entity_code":       { "type": "keyword" },
      "entity_name":       {
        "type": "text",
        "fields": { "keyword": { "type": "keyword" } }
      },
      "location": { "type": "geo_point" }
    }
  }
}
```
