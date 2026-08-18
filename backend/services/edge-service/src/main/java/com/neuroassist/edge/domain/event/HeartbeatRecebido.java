package com.neuroassist.edge.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event: heartbeat accepted and stamped by the server. Published in-process after
 * transaction commit via @TransactionalEventListener(AFTER_COMMIT). Zero Spring/JPA imports
 * (RULE-01, RULE-02).
 */
public record HeartbeatRecebido(
        UUID deviceId,
        String tenantRef,
        Instant at,
        String agentVersion,
        Long runningProfileVersion) {

    public static HeartbeatRecebido of(
            UUID deviceId,
            String tenantRef,
            Instant at,
            String agentVersion,
            Long runningProfileVersion) {
        return new HeartbeatRecebido(deviceId, tenantRef, at, agentVersion, runningProfileVersion);
    }
}
