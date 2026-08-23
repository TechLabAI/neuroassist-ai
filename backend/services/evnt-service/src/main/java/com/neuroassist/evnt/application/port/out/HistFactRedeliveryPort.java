package com.neuroassist.evnt.application.port.out;

/**
 * Outbound port — redelivers a stored outbox payload to hist-service verbatim (E2E-017).
 *
 * <p>Takes the stored JSON rather than a domain object: the entry was serialised at the moment the
 * fact happened, and re-deriving it later risks sending something subtly different from the
 * original attempt. The {@code factId} above all must be identical — that is what makes
 * hist-service deduplicate instead of appending the fact a second time (HIST ADR-005, defect C-5).
 */
public interface HistFactRedeliveryPort {

    /**
     * @return {@code true} when hist-service accepted the fact
     */
    boolean redeliver(String payloadJson);
}
