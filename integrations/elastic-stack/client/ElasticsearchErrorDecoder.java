package com.neuroassist.elastic.client;

import com.neuroassist.elastic.ports.ElasticException;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

/**
 * Translates Elasticsearch client exceptions into {@link ElasticException}.
 *
 * <p>The native ES Java client raises two types of exceptions:
 * <ul>
 *   <li>{@link co.elastic.clients.elasticsearch._types.ElasticsearchException} —
 *       a 4xx/5xx response from the ES server (carries HTTP status and error metadata).</li>
 *   <li>{@link java.io.IOException} — a transport-level failure (connection refused,
 *       timeout, network partition).</li>
 * </ul>
 *
 * <p>This utility class is the single place where ES-specific exception types are
 * referenced in the infrastructure layer. All callers ({@code ElasticsearchAdapter})
 * delegate here so that future changes to ES client exception hierarchy only
 * require updating this class.
 *
 * <p>This is NOT a Feign {@code ErrorDecoder}. The native client does not use Feign.
 *
 * <p>D-268: native client exception translation.
 */
@Slf4j
public final class ElasticsearchErrorDecoder {

    private ElasticsearchErrorDecoder() {}

    /**
     * Translate an {@link ElasticsearchException} (4xx/5xx server response).
     *
     * <p>The exception message includes the HTTP status and the ES error reason.
     * 4xx errors indicate a client-side issue (bad query, missing index, mapping
     * conflict) and should not be retried. 5xx errors indicate a server-side
     * issue and may be retried after a backoff.
     *
     * @param e the ES exception from the native client
     * @return a typed {@link ElasticException} wrapping the original
     */
    public static ElasticException decode(ElasticsearchException e) {
        int status = e.status();
        String reason = e.error() != null ? e.error().reason() : e.getMessage();
        String msg = String.format("[%d] Elasticsearch error: %s", status, reason);
        log.warn("Elasticsearch server error: {}", msg);
        return new ElasticException(msg, e, status);
    }

    /**
     * Translate an {@link IOException} (transport-level failure).
     *
     * <p>Transport errors have HTTP status 0 (no response was received).
     * They include connection timeouts, refused connections, and network partitions.
     *
     * @param e         the transport exception
     * @param operation description of the operation that failed (for log context)
     * @return a typed {@link ElasticException} wrapping the original
     */
    public static ElasticException decode(IOException e, String operation) {
        String msg = "Elasticsearch transport error during [" + operation + "]: " + e.getMessage();
        log.warn("{}", msg);
        return new ElasticException(msg, e, 0);
    }
}
