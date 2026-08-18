package com.neuroassist.edge.api.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Response for GET /api/v1/edge/devices/{deviceId}/liveness and GET
 * /api/v1/edge/patients/{pacienteId}/liveness. status is derived at read time (ADR-006). serverTime
 * is mandatory — EDGE-004 / M3 warning closure.
 */
public record LivenessResponse(
        UUID deviceId,
        UUID pacienteId,
        String status,
        Instant ultimaSincronizacao,
        String versao,
        long stalenessThresholdSeconds,
        Instant serverTime) {}
