package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/** Thrown when a caregiver-patient link crosses tenant boundaries (D-204). Maps to HTTP 422. */
public class CrossTenantLinkException extends CodedException {

    public CrossTenantLinkException(UUID cuidadorTenantId, UUID pacienteTenantId) {
        super("acs.link.cross-tenant-link", "Caregiver and patient belong to different tenants");
        withContext(
                Map.of(
                        "cuidadorTenantId", cuidadorTenantId.toString(),
                        "pacienteTenantId", pacienteTenantId.toString()));
    }
}
