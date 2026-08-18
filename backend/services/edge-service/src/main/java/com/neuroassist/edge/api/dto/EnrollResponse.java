package com.neuroassist.edge.api.dto;

import java.util.UUID;

/**
 * Response for POST /api/v1/edge/devices/enroll. clientSecret returned exactly once — only its hash
 * persisted.
 */
public record EnrollResponse(
        UUID deviceId,
        String tenantRef,
        String clientId,
        String clientSecret,
        String tokenEndpoint,
        String tokenScope,
        int recommendedHeartbeatSeconds) {}
