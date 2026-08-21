package com.neuroassist.hist.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Query parameters for {@code GET /api/v1/hist/history}. All fields are optional and combined with
 * AND logic. Used by the history controller (P4 implementation, HIST-013).
 */
public record HistoryQueryRequest(
        String subjectType,
        UUID subjectId,
        String factType,
        UUID actorCaregiverId,
        Instant from,
        Instant to,
        String order,
        Integer page,
        Integer size) {}
