package com.neuroassist.hist.application.port.in;

import java.util.List;

/**
 * Inbound port — read-only access to the active controlled vocabulary. Exposed at {@code GET
 * /api/v1/hist/fact-types}.
 */
public interface FactTypeCatalogUseCase {

    /**
     * A single entry in the active vocabulary.
     *
     * @param code dot-notation code, e.g. {@code "evento.confirmado"}
     * @param version vocabulary version this code belongs to, e.g. {@code "v1"}
     */
    record FactTypeEntry(String code, String version) {}

    /** Returns the list of all active fact-type codes in the current vocabulary version. */
    List<FactTypeEntry> listActive();
}
