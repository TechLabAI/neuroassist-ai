package com.neuroassist.acs.application.port.out;

import java.util.UUID;

/**
 * Outbound port — MigProvisioningPort. Communicates with mig-service to provision tenant schemas.
 * Timeout: 5s (fail-closed). Circuit breaker enforced at adapter level. Zero Spring/JPA imports
 * (RULE-01, RULE-02, RULE-06).
 */
public interface MigProvisioningPort {

    /**
     * Requests creation of a new tenant schema. Idempotent: same (tenantId, schemaName) returns the
     * same result without re-creating the schema.
     *
     * @param tenantId UUID of the tenant being provisioned
     * @param schemaName target schema name (e.g. "t_<uuid>")
     * @return MigSchemaResponse with status and schemaName
     */
    MigSchemaResponse requestTenantSchema(UUID tenantId, String schemaName);

    /**
     * Polls the provisioning status of a tenant schema.
     *
     * @param tenantId UUID of the tenant
     * @return MigSchemaStatusResponse with current provisioning status
     */
    MigSchemaStatusResponse getTenantSchemaStatus(UUID tenantId);

    /**
     * Response from schema creation request.
     *
     * @param tenantId UUID of the tenant
     * @param schemaName resolved schema name
     * @param status current provisioning status (PENDING, PROVISIONING, READY, FAILED)
     */
    record MigSchemaResponse(UUID tenantId, String schemaName, String status) {}

    /**
     * Polling response for provisioning status.
     *
     * @param tenantId UUID of the tenant
     * @param status current status (PENDING, PROVISIONING, READY, FAILED)
     * @param percentual completion percentage (0-100)
     * @param estimatedSeconds estimated seconds until completion (null if unknown)
     */
    record MigSchemaStatusResponse(
            UUID tenantId, String status, int percentual, Integer estimatedSeconds) {}
}
