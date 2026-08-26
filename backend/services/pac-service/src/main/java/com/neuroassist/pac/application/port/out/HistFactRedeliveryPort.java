package com.neuroassist.pac.application.port.out;

/**
 * Outbound port — redelivers an outbox entry to hist-service verbatim.
 *
 * <p>Takes the stored JSON rather than a domain object: the entry was serialised at the moment the
 * fact happened, and re-deriving it later would risk sending something subtly different from what
 * the original attempt sent. In particular the {@code factId} must be the same, since that is what
 * makes hist-service deduplicate instead of appending the fact twice (HIST ADR-005).
 */
public interface HistFactRedeliveryPort {

    /**
     * @return {@code true} when hist-service accepted the fact
     */
    boolean redeliver(String payloadJson);
}
