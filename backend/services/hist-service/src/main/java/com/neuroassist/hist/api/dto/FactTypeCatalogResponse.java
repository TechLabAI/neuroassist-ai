package com.neuroassist.hist.api.dto;

/**
 * A single entry in the active fact-type vocabulary. Returned as a list from {@code GET
 * /api/v1/hist/fact-types}.
 */
public record FactTypeCatalogResponse(

        /** Dot-notation code, e.g. {@code "evento.confirmado"}. */
        String code,

        /** Vocabulary version this code belongs to, e.g. {@code "v1"}. */
        String version) {}
