
# RAG Workflow Design

> **Project:** NeuroAssist AI
> **Retrieval layer:** Elasticsearch 9.4.1 (this overlay)
> **Orchestration layer:** TechLab AI (see `integrations/techlab-ai/integration-contract.md`)
> **D-274:** Fill one copy of this document per RAG workflow.

---

## Artifact Identity

```yaml
artifact_name:     rag-workflow-<WORKFLOW_SLUG>
artifact_category: design
artifact_role:     Evidence-grounded RAG workflow using Elasticsearch + TechLab AI
artifact_dependencies:
  - integrations/elastic-stack/integration-contract.md
  - integrations/techlab-ai/integration-contract.md
  - index-design-<INDEX_SLUG>.md     # indices providing evidence
artifact_consumers:
  - application service              # orchestrates the workflow
  - TechLab AI                       # reasons over retrieved evidence
```

---

## Execution Contract

Use this template **once per RAG workflow** in `NeuroAssist AI`. A RAG
workflow is a structured process where:
1. A question or intent arrives
2. Elasticsearch retrieves evidence documents
3. TechLab AI generates a grounded answer citing the retrieved evidence

**Evidence-grounded constraint (non-negotiable):** Every AI-generated answer MUST
cite the Elasticsearch document IDs it was grounded on. Answers without evidence
citations are not allowed. This is the central operating rule of the RAG architecture.

---

## Input Schema

```yaml
workflow_name:           # human-readable name
workflow_slug:           # kebab-case identifier
domain_context:          # which module or use case this serves
question_types:          # list: types of questions this workflow handles
  - numeric_aggregation  # e.g. "how many X?", answered by ES aggregation only
  - factual_lookup       # e.g. "what is the value of X?", answered by ES search
  - explanatory          # e.g. "why?", requires ES retrieval + TechLab AI synthesis
  - geo_analytical       # e.g. "where?", requires ES geo + TechLab AI summary
  - semantic_hybrid      # e.g. "find docs about X", vector/semantic + RRF fusion (D-300)
retrieval_indices:       # list: index patterns to retrieve from
max_hits_per_query:      # max ES hits to pass to TechLab AI (recommend: 5–20)
structured_output:       # boolean: does TechLab AI return a typed JSON response?
```

---

## Output Schema

```yaml
workflow_steps:          # ordered list of steps with input/output
retrieval_strategy:      # how ES queries are constructed from user intent
evidence_format:         # how ES hits are serialized before passing to TechLab AI
ai_endpoint:             # which TechLab AI endpoint is called
answer_structure:        # schema of the structured answer (if structured_output: true)
evidence_citation_format: # how document IDs are embedded in the answer
fallback_behavior:       # what happens when ES returns zero hits
```

---

## Generation Rules

1. **Elasticsearch retrieves; TechLab AI reasons.** Never pass a raw user question
   directly to TechLab AI without first retrieving evidence from Elasticsearch.
   This is the architectural boundary defined in `architecture-03-integrated-*`.

2. **Evidence must be cited.** The TechLab AI prompt MUST instruct the model to
   cite the ES document IDs (`_id` fields) in the response. Uncited assertions
   are treated as hallucinations.

3. **Classify the question type first.** Numeric aggregation questions are answered
   by ES alone (no LLM call). This avoids unnecessary AI costs and latency.

4. **Cap the evidence context.** Pass at most `max_hits_per_query` documents to
   TechLab AI. Large evidence sets increase token cost and can exceed context
   limits. Summarize or chunk if needed.

5. **Use TechLab AI's prompt registry.** Do not inline prompt text in the
   application service. Register the RAG prompt in TechLab AI's prompt registry
   and reference it by name and version.

6. **Multi-tenancy propagation.** If the project uses multi-tenancy
   (`multi_tenancy_mode: multi-tenant`),
   the ES query MUST include a tenant filter and TechLab AI MUST receive the
   tenant context in the request headers (see `integration-contract.md` §5).

7. **Structured output.** Prefer TechLab AI's structured output endpoint for
   programmatic consumers. Free-text answers are for user-facing prose only.

---

## §1 — Workflow Overview

**Name:** _(fill in)_

**Domain context:** _(fill in: which module or use case)_

**Question types handled:**
- [ ] Numeric aggregation (ES only — no LLM)
- [ ] Factual lookup (ES + TechLab AI)
- [ ] Explanatory / analytical (ES + TechLab AI synthesis)
- [ ] Geo-analytical (ES geo query + TechLab AI summary)
- [ ] Semantic / hybrid (ES retrievers + RRF — vector/`semantic_text`; requires ES 8.16+)

---

## §2 — Workflow Steps

```
Step 1 — Intent classification
  Input:  user question (string)
  Output: question_type ∈ {numeric_aggregation | factual_lookup | explanatory | geo_analytical}
  Actor:  application service (rule-based or TechLab AI classifier)

Step 2 — Query construction
  Input:  user question + question_type
  Output: Elasticsearch Query DSL (Map<String, Object>)
  Actor:  application service (domain query builder)
  Notes:  for numeric_aggregation, the query includes aggregations; no source needed

Step 3 — Elasticsearch retrieval
  Input:  query DSL + index pattern
  Output: ElasticHits (id, index, score, source)
  Actor:  SearchPort.search() via ElasticsearchAdapter
  Notes:  cap at max_hits_per_query; add tenant filter if multi-tenancy enabled

Step 4 — Short-circuit for numeric_aggregation
  Input:  ElasticHits with aggregation result
  Output: structured numeric answer (no LLM call)
  Actor:  application service
  Notes:  SKIP Steps 5–7 for this type

Step 5 — Evidence formatting
  Input:  ElasticHits
  Output: evidence list: [{id, index, score, relevant_fields}]
  Actor:  application service (evidence serializer)
  Notes:  include traceability fields (source_file_id, line_number) in each evidence item

Step 6 — TechLab AI call
  Input:  user question + evidence list + prompt name/version
  Output: structured answer with citations
  Actor:  TechLab AI (via KnowledgePort or orchestration endpoint)
  Notes:  prompt must instruct the model to cite ES _id values

Step 7 — Response validation
  Input:  TechLab AI answer
  Output: validated answer (citations present, structure conforms to schema)
  Actor:  application service
  Notes:  reject answers with zero citations; log a warning and return a fallback
```

---

## §3 — Retrieval Strategy

Describe how the application service constructs the Elasticsearch query from user intent.

| Question type | Query DSL strategy | Aggregations | Source fields |
|---|---|---|---|
| numeric_aggregation | `match_all` or filter on entity type | `value_count` / `terms` / `sum` | None (aggs only) |
| factual_lookup | `multi_match` on relevant fields | None | All traceability + key domain fields |
| explanatory | `multi_match` or `bool` with boost | Optional | All fields (full source) |
| geo_analytical | `geo_bounding_box` or `geo_distance` | `terms` on state/municipality | Geo fields + traceability |
| semantic_hybrid | `rrf` retriever over `standard` (BM25) + `knn`/`semantic` | Optional | Full source + score |

> **Semantic/hybrid (D-300).** Requires a `dense_vector` or `semantic_text` field in the
> index mapping and ES 8.16+ (retrievers GA). Prefer hybrid (BM25 + vector fused by RRF,
> `rank_constant: 60`) over pure vector — it is more robust with almost no tuning. The
> query is still a `Map<String,Object>` passed to `SearchPort.search()`; the top-level key
> is `retriever` instead of `query`. See the index's `query-capability` contract §6 (D-299).

**Specific query DSL for this workflow:**

_(Describe the query construction logic for each question type this workflow handles.
Include the field list, filter criteria, and any scoring customizations.)_

---

## §4 — Evidence Format

Describe how ES hits are serialized before passing to TechLab AI.

```json
{
  "evidence": [
    {
      "id":              "<ES _id>",
      "index":           "<index name>",
      "score":           0.85,
      "source_file_id":  "<traceability>",
      "line_number":     42,
      "register_code":   "EXAMPLE",
      "relevant_fields": {}
    }
  ],
  "total_hits": 10,
  "query_summary": "search for X in index Y"
}
```

---

## §5 — TechLab AI Integration

| Attribute | Value |
|---|---|
| **Endpoint** | _(e.g. `POST /v1/orchestration/answer` or `POST /v1/knowledge/ingest`)_ |
| **Prompt name** | _(registered in TechLab AI prompt registry)_ |
| **Prompt version** | _(pinned version, updated via `sync-techlab-ai.py`)_ |
| **Structured output** | _(yes/no — if yes, provide JSON schema)_ |
| **Max input tokens** | _(estimate based on evidence count × avg doc size)_ |
| **Tenant propagation** | Not applicable (single-tenant) |

---

## §6 — Answer Structure

If structured output is enabled, define the JSON schema for the answer:

```json
{
  "answer":     "string — the generated answer",
  "citations":  ["<ES _id>", ...],
  "confidence": "high | medium | low",
  "evidence_count": 3
}
```

---

## §7 — Fallback Behavior

| Condition | Behavior |
|---|---|
| ES returns zero hits | Return a "no evidence found" response; do NOT call TechLab AI |
| TechLab AI returns uncited answer | Reject; return a structured error with `reason: "no_citations"` |
| TechLab AI timeout / 5xx | Retry up to 2 times; fall back to ES-only aggregation if available |
| ES timeout / network error | Surface `ElasticException`; do not degrade to hallucination |

---

## §8 — Observability

This workflow emits the following metrics and logs:

| Event | Type | Notes |
|---|---|---|
| `rag.question.received` | counter | tagged by `question_type` |
| `rag.retrieval.hits` | histogram | number of ES hits returned |
| `rag.retrieval.latency_ms` | histogram | ES search latency |
| `rag.ai.called` | counter | TechLab AI calls (short-circuit excluded) |
| `rag.ai.latency_ms` | histogram | TechLab AI response latency |
| `rag.answer.uncited` | counter | answers rejected for missing citations |
| `rag.shortcircuit.numeric` | counter | questions answered by ES aggregation alone |

---

## §9 — Example Interaction

**Question:** _(fill in: example user question)_

**Question type:** _(fill in)_

**ES query:** _(sketch the query DSL)_

**ES response (truncated):**
```json
{ "hits": { "total": 5, "hits": [{ "_id": "example_001", "_source": {} }] } }
```

**TechLab AI prompt (summary):**
> "Given the following evidence from Elasticsearch, answer the question and cite
> the document IDs you used: ..."

**Answer:**
```json
{
  "answer": "Based on documents example_001 and example_002, ...",
  "citations": ["example_001", "example_002"],
  "confidence": "high",
  "evidence_count": 2
}
```
