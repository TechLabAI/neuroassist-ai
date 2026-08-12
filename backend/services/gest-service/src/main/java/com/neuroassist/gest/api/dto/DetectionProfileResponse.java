package com.neuroassist.gest.api.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/** Response shape for a DetectionProfile resource. */
public record DetectionProfileResponse(
        UUID id,
        long version,
        boolean active,
        String contractVersion,
        Map<String, Object> classificationParams,
        Instant publishedAt,
        String etag) {}
