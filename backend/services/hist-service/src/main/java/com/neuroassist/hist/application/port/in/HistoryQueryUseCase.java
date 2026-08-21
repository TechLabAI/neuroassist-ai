package com.neuroassist.hist.application.port.in;

import com.neuroassist.hist.domain.model.AuditRecord;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port — paginated timeline query over the tenant ledger. Exposed at {@code GET
 * /api/v1/hist/history}. Requires {@code hist:read} or {@code hist:read:all} scope.
 */
public interface HistoryQueryUseCase {

    /**
     * Filter/pagination parameters for a timeline query. All fields are optional (null = no filter
     * applied).
     */
    record Query(
            String subjectType,
            UUID subjectId,
            String factType,
            UUID actorCaregiverId,
            Instant from,
            Instant to,
            String order,
            int page,
            int size,
            String tenantId) {}

    /** Paginated result page. */
    record HistoryPage(List<AuditRecord> items, int page, int size, long total) {}

    /**
     * Returns a paginated, filtered slice of the tenant ledger. Records from other tenants are
     * never returned.
     *
     * @param query query parameters including tenantId
     * @return paginated result ordered by {@code recordedAt} per {@code query.order()}
     */
    HistoryPage queryTimeline(Query query);
}
