package com.neuroassist.mig.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * Response of {@code PUT /internal/migrations/tenants/{tenantId}} (api-contract §1.1).
 *
 * @param status {@code READY} when the schema exists and is at head. The vocabulary is
 *     acs-service's, not the draft's {@code "provisioned"} — see {@code ProvisioningStatus}.
 * @param appliedVersions empty when the call was a no-op because the tenant was already at head
 */
public record ProvisionTenantResponse(
        UUID tenantId,
        String schema,
        String status,
        List<String> appliedVersions,
        String currentVersion) {}
