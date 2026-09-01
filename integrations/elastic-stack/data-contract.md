
# Elasticsearch Data Contract

> **This project:** NeuroAssist AI
> **Index prefix:** `neuroassist-ai_`
> **Traceability rule:** Operating Rule "preserve auditability" — D-269

---

## 1. Traceability Block (mandatory in every indexed document)

Every document indexed into any `neuroassist-ai_*` index
MUST carry all 8 traceability fields. This is a **hard constraint**, not optional.

```json
{
  "source_file_id":    "<keyword> — stable identifier of the source artifact or file",
  "line_number":       "<long>    — 1-based line/record number in the source; 0 if not applicable",
  "raw_line":          "<text>    — verbatim source line or serialized raw record; stored not analyzed",
  "register_code":     "<keyword> — record type code from the source schema (e.g. 'C170', 'EVENT')",
  "block":             "<keyword> — logical block or section of the source ('0', 'C', 'A', ...)",
  "parse_status":      "<keyword> — ingestion outcome: 'ok' | 'partial' | 'error' | 'skipped'",
  "ingested_at":       "<date>    — ISO-8601 UTC timestamp of indexing",
  "synthetic_dataset": "<boolean> — true when the source is synthetic/test data"
}
```

### Enforcement

- The `ElasticsearchAdapter.bulkIndex()` implementation validates that each
  document map contains all 8 keys before submitting to `_bulk`.
- Missing fields cause the document to be routed to the Dead Letter Index
  (`neuroassist-ai_dlq`) with
  `parse_status: "error"` and an `_error` field describing the violation.
- Integration tests verify this contract via `IndexPort` smoke assertions.

---

## 2. Geo Enrichment Block (required for participant/location indices)

Indices storing geo-located entities (participants, locations, facilities) must
carry these additional fields when geo data is available:

```json
{
  "location":        "<geo_point> — {lat, lon}",
  "municipality_id": "<keyword>   — official municipality code",
  "municipality":    "<text+keyword> — municipality name",
  "state_code":      "<keyword>   — 2-letter state/province code",
  "country_code":    "<keyword>   — ISO 3166-1 alpha-2"
}
```

Fields may be null/absent when geo data is not available for the entity.
Kibana Maps requires `geo_point` mapping; use `geo_shape` only for polygon data.

---

## 3. Document Types

The table below is populated by `/be-architect-specs <MODULE>`. Each row describes
one document type (one Elasticsearch index or index pattern).

| Index | Class | Granularity | Traceability | Geo | Declared in |
|---|---|---|---|---|---|
| `neuroassist-ai_arvore-fala` | dimension | one document per (node, locale) | required (8 fields; TREI populates) | — | CONV — `docs/architecture/backend/services/conv-service/design/index-design-arvore-fala.md` (`/be-architect-specs CONV`) |

---

## 4. Field Type Conventions

Apply consistently across all indices:

| Pattern | Type | Notes |
|---|---|---|
| Identifiers and codes | `keyword` | No analysis; exact match and aggregation |
| Human-readable names (search + agg) | `text` with `fields.keyword: keyword` | Full-text search on `text`; aggregation on `keyword` |
| Numeric quantities (counts, amounts) | `integer` / `long` / `double` | Choose precision; avoid `float` for monetary values |
| Timestamps | `date` | ISO-8601 UTC; use `strict_date_optional_time` format |
| Booleans | `boolean` | Never store as `keyword "true"/"false"` |
| Large free text (raw lines, descriptions) | `text` with `index: false` | Stored, not indexed; reduces heap usage |
| Geo points | `geo_point` | Use `{lat, lon}` object form |

**Do not** rely on dynamic mapping for business-critical fields. Every index
must have an explicit mapping defined before first document is indexed.

---

## 5. Bulk Ingestion Contract

When using `IndexPort.bulkIndex()` / `ElasticsearchAdapter`:

- **Batch size:** 1,000–5,000 documents per call
- **Document ID:** Provide deterministic IDs (`source_file_id + "_" + line_number`)
  to enable safe replay without duplicates
- **Idempotency:** Same document ID with same content is a no-op (`op_type: index`
  with `version_type: external` is optional for versioning)
- **DLQ routing:** Documents that fail validation or ES indexing errors are
  routed to `neuroassist-ai_dlq`

---

## 6. Raw-Line Storage Decision

Projects must decide at index design time:

| Option | Trade-off |
|---|---|
| **A — inline** | Store `raw_line` as a field on normalized documents. Simple, single index. Cost: larger index size. |
| **B — separate raw index** | Keep `neuroassist-ai_raw` with shorter ILM retention. Full traceability + replay; separate billing tier. |

Either option: the `raw_line` field in the traceability block is the canonical
reference. If Option B is chosen, `raw_line` in the normalized index may be
omitted but `source_file_id` + `line_number` must still be present.

---

## Maintenance

- This file is rendered once by `/new-project`.
- Document type rows (§3) are added by `/be-architect-specs <MODULE>`.
- The 8-field traceability block (§1) is immutable — do not remove fields.
