package com.neuroassist.mig.api.dto;

import java.util.UUID;

/**
 * Response of {@code GET /internal/migrations/tenants/{tenantId}}.
 *
 * <p>Mirrors acs-service's {@code MigProvisioningPort.MigSchemaStatusResponse} field for field, so
 * the consumer record is a transcription rather than an interpretation.
 *
 * @param percentual 100 only when READY — a progress number that reaches 100 before the schema is
 *     usable is worse than no progress number
 */
public record TenantSchemaStatusResponse(
        UUID tenantId, String status, int percentual, Integer estimatedSeconds) {}
