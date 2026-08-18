package com.neuroassist.edge.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for POST /api/v1/edge/heartbeat. status is derived at response time (never a stored
 * column, ADR-006). When profilePointerDegraded=true, currentProfileVersion and profileDrift are
 * null.
 */
public record HeartbeatResponse(
        UUID deviceId,
        String status,
        Instant serverTime,
        Instant ultimaSincronizacao,
        Long currentProfileVersion,
        Boolean profileDrift,
        boolean profilePointerDegraded,
        long stalenessThresholdSeconds,
        long nextHeartbeatSeconds) {}
