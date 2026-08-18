package com.neuroassist.edge.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Response for POST /api/v1/edge/devices/{id}/pairings (201 Created). */
public record PairingResponse(
        UUID deviceId,
        String tenantRef,
        UUID pacienteId,
        String pairingState,
        Instant createdAt,
        Instant updatedAt) {}
