package com.neuroassist.edge.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for POST /api/v1/edge/devices/enroll (exchange token for credential). No tenantId or
 * deviceId — both are derived from the enrollment token. This endpoint requires no Bearer JWT
 * (guarded by single-use token only).
 */
public record EnrollRequest(@NotBlank String enrollmentToken, @NotBlank String agentVersion) {}
