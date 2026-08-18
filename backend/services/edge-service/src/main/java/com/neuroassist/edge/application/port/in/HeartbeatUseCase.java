package com.neuroassist.edge.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port — heartbeat ingestion (EDGE-008, P2). tenantId is resolved internally via
 * TenantContextHolder (set by device JWT in TenantContextFilter). deviceId comes from the
 * authenticated device token (caller's authentication), never the body. ADR-006: stamps
 * ultimaSincronizacao with the server clock.
 */
public interface HeartbeatUseCase {

    /**
     * Record a heartbeat from an edge agent.
     *
     * @param deviceId from the authenticated device token (NOT from body)
     * @param agentVersion agent software version string
     * @param runningProfileVersion the detection-profile version the device is running
     */
    HeartbeatResult recordHeartbeat(UUID deviceId, String agentVersion, Long runningProfileVersion);

    record HeartbeatResult(
            UUID deviceId,
            String status,
            Instant serverTime,
            Instant ultimaSincronizacao,
            Long currentProfileVersion,
            Boolean profileDrift,
            boolean profilePointerDegraded,
            long stalenessThresholdSeconds,
            long nextHeartbeatSeconds) {}
}
