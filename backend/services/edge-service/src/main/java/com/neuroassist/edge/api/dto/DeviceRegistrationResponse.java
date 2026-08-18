package com.neuroassist.edge.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for POST /api/v1/edge/devices (register). enrollmentToken returned exactly once — only
 * hash stored in DB.
 */
public record DeviceRegistrationResponse(
        UUID deviceId,
        String pairingState,
        String label,
        String enrollmentToken,
        Instant enrollmentExpiresAt,
        Instant createdAt) {}
