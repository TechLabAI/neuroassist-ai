
# Elasticsearch Integration Contract

> **This project:** NeuroAssist AI
> **Elasticsearch version:** `9.4.1`
> **Client lib (this project):** `backend/services/elastic-stack-client-lib/`
> **Local port:** `9200` (ES) · `5601` (Kibana)
> **Index prefix:** `neuroassist-ai_`

---

## 1. Overview

This document describes how NeuroAssist AI uses Elasticsearch for
search, analytics, and retrieval. Elasticsearch is deployed as an adjacent
infrastructure component (not a separate team's service); this project owns its
own index schemas and lifecycle policies.

This project does **not** call AI providers directly. Full-text retrieval results
from Elasticsearch are fed to TechLab AI (`integrations/techlab-ai/`) for
reasoning, structured output, and RAG pipelines.

The client library (`elastic-stack-client-lib`) provides:

- Hexagonal ports (`SearchPort`, `IndexPort`) decoupled from ES internals
- `ElasticsearchAdapter` — single adapter implementing both ports
- `ElasticsearchClientConfig` — Spring bean for the native `ElasticsearchClient`
- Exception translation via `ElasticsearchErrorDecoder`

For local development, see `integrations/elastic-stack/docker/compose-elastic.yml`.

---

## 2. Indices owned by this project

The matrix below is populated by `/be-architect-specs <MODULE>` as each module
declares its indices. New rows are appended; deprecated indices are marked, not
removed.

| Module | Index name | Class | Primary use | Declared in |
|---|---|---|---|---|
| _(none yet)_ | — | — | — | _Populated by `/be-architect-specs <MODULE>`._ |

### 2.1 Index class definitions (Dimensions vs Facts — D-272)

| Class | Scaling | Shard strategy | ILM |
|---|---|---|---|
| **dimension** | Bounded by company universe (finite set: participants, catalog, geo) | 1 primary shard, no ILM | None or long hot phase |
| **fact** | Linear with data volume (documents, line items, events) | Size shards for projected doc count; index sorting on `register_code`/`uf` | Rollover + hot/warm with configurable retention |

Every index design document (`design/index-design.md`) must classify the index
and justify the shard/ILM strategy.

### 2.2 Traceability block (mandatory — D-269)

Every indexed document must carry the 8-field traceability block. See
`integrations/elastic-stack/data-contract.md` for the full contract.

---

## 3. Client configuration

All ES calls go through the `ElasticsearchClient` Spring bean, constructed in
`ElasticsearchClientConfig`:

```yaml
elasticsearch:
  url: http://localhost:9200
  connect-timeout-ms: 2000
  socket-timeout-ms: 30000
```

For multi-node clusters, set `elasticsearch.url` to a comma-separated list of
node URLs and configure `ElasticsearchClientConfig` with `RestClientBuilder`
multi-node constructor. See `compose-elastic.yml` comments.

---

## 4. Error handling

The native client raises `co.elastic.clients.elasticsearch._types.ElasticsearchException`
for 4xx/5xx responses, and `java.io.IOException` for transport errors.

`ElasticsearchErrorDecoder` translates both into `ElasticException`:

| Condition | Behavior |
|---|---|
| 400, 422 | `ElasticException(status=4xx)` — query/mapping issue; not retried |
| 404 | `ElasticException(status=404)` — index or document missing |
| 429 | `ElasticException(status=429)` — too many requests; check bulk sizing |
| 5xx | `ElasticException(status=5xx)` — retried by caller policy |
| `IOException` | `ElasticException(status=0)` — transport error |

Application services should catch `ElasticException` at the port boundary.
Infrastructure-level retry (for 5xx / transport) is the caller's responsibility.

---

## 5. Bulk indexing

Use `IndexPort.bulkIndex()` for batch writes. Guidelines:

- Batch size: 1,000–5,000 documents per call (aligns with ActiveMQ message batches)
- During initial load: set `refresh_interval: -1` and `number_of_replicas: 0`
  via the ES Index Settings API, restore after load
- For fact indices: use `BulkIngester` (async, flow-controlled) rather than
  synchronous `_bulk` for volumes above 100k documents

---

## 6. Security

**Local development:** security disabled (no TLS, no auth) — see `compose-elastic.yml`.

**Production/staging:** Enable Elasticsearch security:

```yaml
elasticsearch:
  username: ${ELASTICSEARCH_USERNAME}
  password: ${ELASTICSEARCH_PASSWORD}
  # or: api-key: ${ELASTICSEARCH_API_KEY}
  ssl:
    enabled: true
    certificate-fingerprint: ${ELASTICSEARCH_CERT_FINGERPRINT}
```

Update `ElasticsearchClientConfig` to supply credentials via
`CredentialsProvider` or `ApiKey` header.

---

## 7. RAG integration with TechLab AI

Elasticsearch retrieval feeds the RAG pipeline in TechLab AI. The recommended
flow (see `design/rag-workflow-design.md`):

1. Application service calls `SearchPort.search()` with a structured query
2. Returns `ElasticHits` (id, index, source map, score)
3. Application service passes hits as grounded evidence to TechLab AI
   (via `KnowledgePort.ingest()` or the `orchestration.answer` endpoint)
4. TechLab AI generates structured answer citing ES document IDs

**Do not** pass raw Elasticsearch responses directly to AI providers.
Always pass through TechLab AI for prompt versioning, cost accounting,
and audit logging.

---

## 8. Index lifecycle management

Fact indices use ILM policies managed by the project's Elasticsearch operator
or by the application team. Default policy names follow:
`neuroassist-ai-fact-policy`.

Dimension indices do not use ILM unless they grow unexpectedly (log a warning
if a dimension index exceeds 100k documents — that likely indicates a data model
error).

---

## 9. Local development

Start the local stack:

```bash
docker compose -f integrations/elastic-stack/docker/compose-elastic.yml up -d
# Elasticsearch: http://localhost:9200
# Kibana:        http://localhost:5601
```

Verify:

```bash
curl http://localhost:9200/_cluster/health?pretty
```

---

## 10. Versioning policy

This project pins Elasticsearch to `9.4.1`.
To upgrade:

1. Update `elasticsearch_version` in `project-context.json`
2. Re-render this file and `build.gradle.kts`
3. Test against the new version with `docker compose up`
4. Update ILM policies and mappings if the ES mapping API changed

**Breaking changes** in Elasticsearch (e.g., mapping API, query DSL changes)
require reviewing affected adapters and index design documents.

---

## Maintenance

- This file is rendered once by `/new-project`.
- The index matrix (§2) is updated by `/be-architect-specs <MODULE>`.
- Index design documents live at `docs/architecture/<module>/design/`.
- Data contract: `integrations/elastic-stack/data-contract.md`.
