package com.neuroassist.edge.api.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Request body for POST /api/v1/edge/devices/{id}/pairings (pair device to patient). pacienteId is
 * a cross-service reference — not validated against PAC at pairing time. tenantId is NOT in this
 * record — derived exclusively from the JWT.
 */
public record PairingRequest(@NotNull UUID pacienteId) {}
