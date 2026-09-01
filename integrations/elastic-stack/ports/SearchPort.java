package com.neuroassist.elastic.ports;

import java.util.List;
import java.util.Map;

/**
 * Hexagonal port for Elasticsearch <b>read</b> operations.
 *
 * <p>Application services depend on this interface, not on the ES Java client
 * directly. The implementing adapter ({@code ElasticsearchAdapter}) lives in the
 * infrastructure layer and is the only class that imports
 * {@code co.elastic.clients}.
 *
 * <p>Query DSL is passed as a plain {@code Map<String, Object>} to avoid
 * coupling callers to ES query builder types. Callers may use
 * {@code Map.of("match_all", Map.of())} or build the DSL from a domain-level
 * query object and serialize it before calling this port.
 *
 * <p>Rules:
 * <ul>
 *   <li>This interface lives in the {@code ports} layer — no Spring, no ES imports.</li>
 *   <li>Hits carry their source as {@code Map<String, Object>} (schema-agnostic).</li>
 *   <li>Transport and 5xx errors surface as {@link ElasticException}.</li>
 *   <li>Zero results return an empty {@link ElasticHits}, never an exception.</li>
 * </ul>
 *
 * <p>D-271: SearchPort is read-only; for writes use {@link IndexPort}.
 */
public interface SearchPort {

    /**
     * Execute a search against one or more indices.
     *
     * @param indexPattern index name or wildcard pattern (e.g. {@code myproject_*})
     * @param query        Elasticsearch Query DSL as a plain map
     * @return hits with total count; empty if nothing matched
     * @throws ElasticException on transport error or 5xx response
     */
    ElasticHits search(String indexPattern, Map<String, Object> query);

    /**
     * Count matching documents without returning their sources.
     *
     * @param indexPattern index name or wildcard pattern
     * @param query        Elasticsearch Query DSL as a plain map
     * @return total number of matching documents
     * @throws ElasticException on transport error or 5xx response
     */
    long count(String indexPattern, Map<String, Object> query);

    // ── Value objects ──────────────────────────────────────────────────────────

    /**
     * A single Elasticsearch hit — index name, document ID, source fields, and score.
     *
     * @param index  the index the document was retrieved from
     * @param id     the document {@code _id}
     * @param score  relevance score (may be {@code null} for non-scored queries)
     * @param source the document source fields as a map
     */
    record ElasticHit(String index, String id, Double score, Map<String, Object> source) {}

    /**
     * A search result page.
     *
     * @param hits  matching documents; empty list when there are no results
     * @param total total number of matching documents in the index (before pagination)
     */
    record ElasticHits(List<ElasticHit> hits, long total) {

        /** Convenience factory for an empty result. */
        public static ElasticHits empty() {
            return new ElasticHits(List.of(), 0L);
        }
    }
}
