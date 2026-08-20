package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/**
 * Thrown when a caregiver's tenant does not match the requested tenant context. Prevents
 * cross-tenant data access. Maps to HTTP 403.
 */
public class TenantMismatchException extends CodedException {

    public TenantMismatchException(UUID requestedTenantId, UUID actualTenantId) {
        super("acs.tenant.mismatch", "Caregiver does not belong to the requested tenant");
        withContext(
                Map.of(
                        "requestedTenantId", requestedTenantId.toString(),
                        "actualTenantId", actualTenantId.toString()));
    }
}
