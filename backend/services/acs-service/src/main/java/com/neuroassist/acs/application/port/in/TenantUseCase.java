package com.neuroassist.acs.application.port.in;

import com.neuroassist.acs.api.dto.TenantRequest;
import com.neuroassist.acs.api.dto.TenantResponse;
import java.util.UUID;

/**
 * Inbound port — Tenant management use cases. Tenant creation triggers the MIG provisioning SAGA
 * (fail-closed).
 */
public interface TenantUseCase {

    /**
     * Creates and provisions a new tenant (SAGA: PROVISIONING → ACTIVE).
     *
     * @param request tenant creation data (nome, tipo)
     * @return TenantResponse once persisted with PROVISIONING status
     */
    TenantResponse createTenant(TenantRequest request);

    /**
     * Retrieves a tenant by its UUID.
     *
     * @param tenantId the tenant UUID
     * @return TenantResponse
     */
    TenantResponse findById(UUID tenantId);

    /**
     * Retries provisioning for a tenant stuck in PROVISIONING status.
     *
     * @param tenantId the tenant UUID to retry
     * @return updated TenantResponse
     */
    TenantResponse retryProvisioning(UUID tenantId);
}
