package com.neuroassist.elastic.adapters;

import com.neuroassist.elastic.client.ElasticsearchErrorDecoder;
import com.neuroassist.elastic.ports.ElasticException;
import com.neuroassist.elastic.ports.IndexPort;
import com.neuroassist.elastic.ports.SearchPort;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.CountRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.core.SearchRequest;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Elasticsearch adapter — implements both {@link SearchPort} and {@link IndexPort}.
 *
 * <p>This is the <b>only</b> class in the project that imports
 * {@code co.elastic.clients}. Application services use only the port interfaces.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Translate domain-type calls (plain {@code Map}) into typed ES requests.</li>
 *   <li>Validate that indexed documents carry the 8-field traceability block (D-269).</li>
 *   <li>Route documents that fail traceability validation to the DLQ index.</li>
 *   <li>Translate ES exceptions into {@link ElasticException} via
 *       {@link ElasticsearchErrorDecoder}.</li>
 * </ul>
 *
 * <p>D-271: single adapter for both read and write ports.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ElasticsearchAdapter implements SearchPort, IndexPort {

    private static final Set<String> REQUIRED_TRACEABILITY_FIELDS = Set.of(
            "source_file_id", "line_number", "raw_line",
            "register_code", "block", "parse_status",
            "ingested_at", "synthetic_dataset"
    );

    private static final String DLQ_SUFFIX = "_dlq";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};

    private final ElasticsearchClient esClient;
    private final ObjectMapper objectMapper;

    // ── SearchPort ─────────────────────────────────────────────────────────────

    @Override
    public ElasticHits search(String indexPattern, Map<String, Object> query) {
        log.debug("ES search: index={}", indexPattern);
        try {
            byte[] queryBytes = objectMapper.writeValueAsBytes(query);
            SearchResponse<Map> response = esClient.search(
                    SearchRequest.of(s -> s
                            .index(indexPattern)
                            .withJson(new ByteArrayInputStream(queryBytes))),
                    Map.class);

            List<ElasticHit> hits = new ArrayList<>();
            for (Hit<Map> hit : response.hits().hits()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> source = hit.source() != null ? hit.source() : Map.of();
                hits.add(new ElasticHit(hit.index(), hit.id(), hit.score(), source));
            }
            long total = response.hits().total() != null
                    ? response.hits().total().value() : hits.size();
            return new ElasticHits(hits, total);
        } catch (ElasticsearchException e) {
            throw ElasticsearchErrorDecoder.decode(e);
        } catch (IOException e) {
            throw ElasticsearchErrorDecoder.decode(e, "search:" + indexPattern);
        }
    }

    @Override
    public long count(String indexPattern, Map<String, Object> query) {
        log.debug("ES count: index={}", indexPattern);
        try {
            byte[] queryBytes = objectMapper.writeValueAsBytes(query);
            var response = esClient.count(
                    CountRequest.of(c -> c
                            .index(indexPattern)
                            .withJson(new ByteArrayInputStream(queryBytes))));
            return response.count();
        } catch (ElasticsearchException e) {
            throw ElasticsearchErrorDecoder.decode(e);
        } catch (IOException e) {
            throw ElasticsearchErrorDecoder.decode(e, "count:" + indexPattern);
        }
    }

    // ── IndexPort ──────────────────────────────────────────────────────────────

    @Override
    public String index(String indexName, String docId, Map<String, Object> document) {
        validateTraceability(indexName, docId, document);
        log.debug("ES index: index={} id={}", indexName, docId);
        try {
            var response = esClient.index(
                    IndexRequest.of(i -> i
                            .index(indexName)
                            .id(docId)
                            .document(JsonData.fromJson(objectMapper.writeValueAsString(document)))));
            return response.id();
        } catch (ElasticsearchException e) {
            throw ElasticsearchErrorDecoder.decode(e);
        } catch (IOException e) {
            throw ElasticsearchErrorDecoder.decode(e, "index:" + indexName + "/" + docId);
        }
    }

    @Override
    public BulkResult bulkIndex(String indexName, List<BulkCommand> commands) {
        if (commands.isEmpty()) {
            return new BulkResult(0, 0, List.of());
        }
        log.debug("ES bulk: index={} count={}", indexName, commands.size());

        List<BulkCommand> valid = new ArrayList<>();
        List<String> failedIds = new ArrayList<>();

        for (BulkCommand cmd : commands) {
            if (hasAllTraceabilityFields(cmd.document())) {
                valid.add(cmd);
            } else {
                log.warn("ES bulk: traceability validation failed for id={} index={}", cmd.docId(), indexName);
                routeToDlq(indexName, cmd, "missing traceability fields");
                failedIds.add(cmd.docId());
            }
        }

        if (valid.isEmpty()) {
            return new BulkResult(0, commands.size(), failedIds);
        }

        try {
            BulkRequest.Builder bulkBuilder = new BulkRequest.Builder();
            for (BulkCommand cmd : valid) {
                String json = objectMapper.writeValueAsString(cmd.document());
                final String id = cmd.docId();
                bulkBuilder.operations(op -> op
                        .index(i -> i
                                .index(indexName)
                                .id(id)
                                .document(JsonData.fromJson(json))));
            }
            BulkResponse response = esClient.bulk(bulkBuilder.build());

            int indexed = 0;
            if (response.items() != null) {
                for (var item : response.items()) {
                    if (item.error() != null) {
                        log.warn("ES bulk item failed: id={} reason={}", item.id(), item.error().reason());
                        failedIds.add(item.id());
                    } else {
                        indexed++;
                    }
                }
            }
            return new BulkResult(indexed, failedIds.size(), failedIds);
        } catch (ElasticsearchException e) {
            throw ElasticsearchErrorDecoder.decode(e);
        } catch (IOException e) {
            throw ElasticsearchErrorDecoder.decode(e, "bulk:" + indexName);
        }
    }

    @Override
    public void deleteIndex(String indexName) {
        log.warn("ES deleteIndex: {}", indexName);
        try {
            esClient.indices().delete(d -> d.index(indexName));
        } catch (ElasticsearchException e) {
            throw ElasticsearchErrorDecoder.decode(e);
        } catch (IOException e) {
            throw ElasticsearchErrorDecoder.decode(e, "deleteIndex:" + indexName);
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private boolean hasAllTraceabilityFields(Map<String, Object> document) {
        return document.keySet().containsAll(REQUIRED_TRACEABILITY_FIELDS);
    }

    private void validateTraceability(String indexName, String docId, Map<String, Object> document) {
        if (!hasAllTraceabilityFields(document)) {
            throw new ElasticException(
                    "Document missing required traceability fields (D-269) — " +
                    "index=" + indexName + " id=" + docId);
        }
    }

    private void routeToDlq(String sourceIndex, BulkCommand cmd, String reason) {
        String dlqIndex = sourceIndex + DLQ_SUFFIX;
        try {
            Map<String, Object> dlqDoc = new java.util.HashMap<>(cmd.document());
            dlqDoc.put("_error", reason);
            dlqDoc.put("_source_index", sourceIndex);
            // Best-effort; do not throw if DLQ indexing fails
            esClient.index(IndexRequest.of(i -> i
                    .index(dlqIndex)
                    .id(cmd.docId())
                    .document(JsonData.fromJson(objectMapper.writeValueAsString(dlqDoc)))));
        } catch (Exception e) {
            log.error("ES DLQ routing failed for id={} dlqIndex={}: {}", cmd.docId(), dlqIndex, e.toString());
        }
    }
}
