package com.neuroassist.hist.api.dto;

/**
 * Query parameters for {@code GET /api/v1/hist/fact-types}. Currently no filter parameters; the
 * full v1 vocabulary is always returned. Reserved for future vocabulary versioning or filtering.
 */
public record FactTypeCatalogRequest() {}
