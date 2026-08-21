package com.neuroassist.hist.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Query parameters for {@code GET /api/v1/hist/history/verify}. {@code subjectType} and {@code
 * subjectId} are required; {@code from}/{@code to} are optional.
 */
public record ChainVerificationRequest(
        String subjectType, UUID subjectId, Instant from, Instant to) {}
