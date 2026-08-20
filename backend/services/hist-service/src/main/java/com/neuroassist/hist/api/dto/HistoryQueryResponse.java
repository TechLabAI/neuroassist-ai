package com.neuroassist.hist.api.dto;

import java.util.List;

/**
 * Paginated response for {@code GET /api/v1/hist/history}. Wraps a page of {@link
 * RecordAuditFactResponse} items with pagination metadata.
 */
public record HistoryQueryResponse(
        List<RecordAuditFactResponse> items, int page, int size, long total) {}
