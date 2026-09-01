package com.neuroassist.elastic.ports;

/**
 * Root exception for all errors interacting with Elasticsearch.
 *
 * <p>Subclasses (when needed) live in the same package and represent
 * recoverable scenarios (e.g. {@code ElasticIndexNotFoundException} carrying
 * the missing index name). Business-level search misses are NOT exceptions —
 * they come back as empty {@code ElasticHits}.
 *
 * <p>This is the only exception type that crosses the port boundary; everything
 * else ({@code co.elastic.clients} exceptions, {@code IOException}, transport
 * errors) is wrapped here by {@code ElasticsearchErrorDecoder}.
 *
 * <p>D-271: port boundary exception — no ES imports.
 */
public class ElasticException extends RuntimeException {

    private final int httpStatus;

    public ElasticException(String message) {
        this(message, null, 0);
    }

    public ElasticException(String message, Throwable cause) {
        this(message, cause, 0);
    }

    public ElasticException(String message, Throwable cause, int httpStatus) {
        super(message, cause);
        this.httpStatus = httpStatus;
    }

    /** HTTP status from the ES response, or {@code 0} for transport-level errors. */
    public int getHttpStatus() {
        return httpStatus;
    }

    /** {@code true} when the error is a 4xx client error (not retried). */
    public boolean isClientError() {
        return httpStatus >= 400 && httpStatus < 500;
    }

    /** {@code true} when the error is a 5xx server error (may be retried). */
    public boolean isServerError() {
        return httpStatus >= 500;
    }
}
