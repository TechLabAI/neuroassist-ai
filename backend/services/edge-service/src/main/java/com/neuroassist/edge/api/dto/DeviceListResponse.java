package com.neuroassist.edge.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response element for GET /api/v1/edge/devices (paged device listing — EDGE P9). livenessStatus is
 * derived at read time and never stored (ADR-006).
 */
public record DeviceListResponse(
        UUID deviceId,
        String label,
        String pairingState,
        String livenessStatus,
        Instant lastSeen,
        Instant registeredAt) {}
