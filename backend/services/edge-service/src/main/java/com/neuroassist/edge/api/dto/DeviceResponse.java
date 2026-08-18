package com.neuroassist.edge.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for GET /api/v1/edge/devices/{deviceId} — full device read model. status, profileDrift
 * are derived at read time.
 */
public record DeviceResponse(
        UUID deviceId,
        String tenantRef,
        UUID pacienteId,
        String pairingState,
        String status,
        Instant ultimaSincronizacao,
        String versao,
        Long runningProfileVersion,
        Long currentProfileVersion,
        Boolean profileDrift,
        long stalenessThresholdSeconds) {}
