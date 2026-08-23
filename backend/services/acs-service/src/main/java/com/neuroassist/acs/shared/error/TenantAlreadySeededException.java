package com.neuroassist.acs.shared.error;

import java.util.UUID;

/**
 * The tenant already has at least one caregiver, so it is past its bootstrap (E2E-029).
 *
 * <p>This is the invariant that lets {@code seedFirstCoordinator} skip the JWT tenant comparison
 * safely: the bootstrap path can run once per tenant and never again, so it cannot become a way to
 * create people inside a company that is already operating.
 */
public class TenantAlreadySeededException extends RuntimeException {

    private final transient UUID tenantId;

    public TenantAlreadySeededException(UUID tenantId) {
        super("tenant already has caregivers, bootstrap is not available: " + tenantId);
        this.tenantId = tenantId;
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
