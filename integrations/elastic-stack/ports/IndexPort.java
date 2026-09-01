package com.neuroassist.elastic.ports;

import java.util.List;
import java.util.Map;

/**
 * Hexagonal port for Elasticsearch <b>write</b> operations.
 *
 * <p>Covers single-document indexing, bulk indexing, and index deletion.
 * Application services depend on this interface; the ES Java client is an
 * implementation detail of {@code ElasticsearchAdapter}.
 *
 * <p>Rules:
 * <ul>
 *   <li>Document IDs should be deterministic (e.g. {@code sourceFileId + "_" + lineNumber})
 *       to enable safe replay without duplicates.</li>
 *   <li>Documents must carry all 8 traceability fields (D-269) — the adapter
 *       validates this before submitting to {@code _bulk}.</li>
 *   <li>Failed bulk items are routed to the DLQ index; the {@link BulkResult}
 *       carries the count and IDs of failed items.</li>
 *   <li>Transport and 5xx errors surface as {@link ElasticException}.</li>
 * </ul>
 *
 * <p>D-271: IndexPort is write-only; for reads use {@link SearchPort}.
 */
public interface IndexPort {

    /**
     * Index a single document.
     *
     * @param indexName target index name (fully qualified, with prefix)
     * @param docId     document {@code _id}; must be stable and unique per source record
     * @param document  document fields as a plain map; must include traceability block (D-269)
     * @return the assigned document {@code _id} (same as {@code docId} if provided)
     * @throws ElasticException on transport error, mapping error, or 5xx response
     */
    String index(String indexName, String docId, Map<String, Object> document);

    /**
     * Bulk-index a batch of documents.
     *
     * <p>Recommended batch size: 1,000–5,000 documents (aligns with the ActiveMQ
     * message batch size). Failed items are routed to the DLQ — they do NOT
     * cause the method to throw.
     *
     * @param indexName target index name (fully qualified)
     * @param commands  list of documents to index; each must include the traceability block
     * @return summary with count of indexed and failed items
     * @throws ElasticException on transport error or complete bulk rejection (5xx)
     */
    BulkResult bulkIndex(String indexName, List<BulkCommand> commands);

    /**
     * Delete an entire index.
     *
     * <p>Use with care. This is a destructive operation. Intended for test
     * teardown and index rotation (ILM manages production deletion).
     *
     * @param indexName index to delete (fully qualified)
     * @throws ElasticException if the index does not exist or on transport error
     */
    void deleteIndex(String indexName);

    // ── Value objects ──────────────────────────────────────────────────────────

    /**
     * A single document to be indexed in a bulk operation.
     *
     * @param docId    document {@code _id}; required (deterministic, stable)
     * @param document document fields; must include traceability block (D-269)
     */
    record BulkCommand(String docId, Map<String, Object> document) {}

    /**
     * Result of a bulk indexing call.
     *
     * @param indexed   number of documents successfully indexed
     * @param failed    number of documents that failed (routed to DLQ)
     * @param failedIds {@code _id} values of failed documents; empty when {@code failed == 0}
     */
    record BulkResult(int indexed, int failed, List<String> failedIds) {

        /** {@code true} when every document was indexed successfully. */
        public boolean isFullSuccess() {
            return failed == 0;
        }
    }
}
