package com.neuroassist.edge.api.dto;

import jakarta.validation.constraints.Size;

/** Request body for DELETE /api/v1/edge/devices/{id}/pairings/current (unpair). */
public record UnpairRequest(@Size(max = 500) String reason) {}
