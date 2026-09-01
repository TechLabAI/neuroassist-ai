
# Playbook — Elasticsearch Troubleshooting

> **Project:** NeuroAssist AI
> **Cluster version:** 9.4.1
> **D-301:** Operational guardrails for the elastic-stack overlay. Pairs with the
> per-index `query-capability` contract (D-299) and the `data-contract` (D-269).

## Artifact Identity

```yaml
artifact_name:     es-troubleshooting-playbook
artifact_category: playbook
artifact_role:     Diagnose and resolve common Elasticsearch request/mapping errors
artifact_dependencies:
  - data-contract.md            # field-type conventions (D-269)
  - query-capability-<INDEX>.md # sanctioned query shapes (D-299)
artifact_consumers:
  - application service          # callers building Query DSL
  - rag-workflow-<WORKFLOW>      # fallback behaviour on query errors
```

## Workflow Purpose

Resolve the recurring class of Elasticsearch errors that show up while building
queries, mappings, and aggregations against the elastic-stack overlay. Every entry
maps an observed HTTP status + message to its root cause and a concrete fix, so a
developer (or an agent) can self-correct without leaving the codebase.

## Completion Target

- The failing request returns `2xx` with the expected hits or aggregation result.
- The fix matches the field-type → capability rule in the `query-capability` contract.
- No mapping was silently mutated — type changes went through a new index + `_reindex`.

## Phase 1 — Triage

1. Read the HTTP status: `4xx` = client error (your request), `5xx` = server/cluster error.
2. Read the full `error.reason` in the response body — it names the offending field or clause.
3. Match the message against the Failure Handling catalogue below.
4. Cross-check the target field's type in `index-design` §6 before changing the query.

### Validation

- [ ] HTTP status classified (client vs server)
- [ ] `error.type` / `error.reason` extracted from the response body
- [ ] Matching failure mode identified below (or escalated as novel)

## Phase 2 — Apply Fix & Re-run

1. Apply the resolution for the matched failure mode.
2. If the fix requires a mapping change, create a new index and `POST _reindex` — never
   attempt to change an existing field's type in place.
3. Re-run the request and confirm the expected result.
4. If a query shape was wrong, update the index's `query-capability` contract so the
   forbidden shape is recorded.

### Validation

- [ ] Request returns the expected `2xx` result
- [ ] Any mapping change went through `_reindex` (no in-place type edit)
- [ ] `query-capability` contract updated if a forbidden shape was found

## Guardrail Checklist

Run through this before sending any non-trivial request:

- [ ] HTTP verb matches the operation — `PUT .../_doc/<id>` needs an id; `POST .../_doc` auto-generates.
- [ ] Every JSON object entry is comma-separated; brackets are balanced.
- [ ] Query parameters sit inside their parent query object (e.g. `type` inside `multi_match`).
- [ ] Multi-field text search uses `multi_match` or `bool` — never a bare `match` on two fields.
- [ ] `size: 0` is at the request root (outside `aggs`) for aggregation-only requests.
- [ ] The aggregation's field type supports it (`date` for `date_histogram`, `keyword` for `terms`).
- [ ] Sub-aggregations are wrapped in an inner `"aggs"` key under the parent bucket.

## Failure Handling

### 404 — `no such index [x]`

- Cause: querying an index that does not exist or was deleted.
- Create the index first (`PUT <index>`), or fix the index name / wildcard in the request.

### 405 — `Incorrect HTTP method for uri, allowed: [x]`

- Cause: wrong HTTP verb for the operation.
- Use `PUT <index>/_doc/<id>` when assigning an id; `POST <index>/_doc` to auto-generate one.

### 400 — `was expecting comma to separate Object entries`

- Cause: missing comma between fields in the JSON body.
- Add the comma between every field pair; validate the JSON structure.

### 400 — `[range] query does not support [<field>]`

- Cause: inner parameters not wrapped in an object.
- Wrap them: `"date": { "gte": "...", "lte": "..." }` — the field name owns a nested object.

### 400 — `malformed query, expected [END_OBJECT] but found [FIELD_NAME]`

- Cause: a parameter (e.g. `type`) placed outside its parent query clause.
- Move the parameter inside the parent query object; re-check bracket nesting.

### 400 — `[match] query doesn't support multiple fields, found [x] and [y]`

- Cause: a single `match` query targeting more than one field.
- Use `multi_match` for one query over many fields, or a `bool` with one clause per field.

### 400 — `Aggregation definition for [size ...], expected a [START_OBJECT]`

- Cause: `size` placed inside the `aggs` block.
- Move `"size": 0` to the request root, before `"aggs"`, to return aggregations only.

### 400 — `Field [x] of type [y] is not supported for aggregation [z]`

- Cause: field mapped as an incompatible type (e.g. `keyword` where `date_histogram` needs `date`).
- Mappings are immutable: create a new index with the correct type, `POST _reindex`, then
  aggregate on the new index. Specify a `format` for non-ISO8601 dates in the mapping.

### 400 — `Found two aggregation type definitions in [x]`

- Cause: sub-aggregations not nested under an inner `aggs` key.
- Wrap child aggregations in an `"aggs"` block inside the parent bucket aggregation.
