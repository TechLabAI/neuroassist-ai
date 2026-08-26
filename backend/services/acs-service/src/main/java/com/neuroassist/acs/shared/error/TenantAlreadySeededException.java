package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/**
 * The tenant already has at least one caregiver, so it is past its bootstrap (E2E-029).
 *
 * <p>This is the invariant that lets {@code seedFirstCoordinator} skip the JWT tenant comparison
 * safely: the bootstrap path can run once per tenant and never again, so it cannot become a way to
 * create people inside a company that is already operating.
 *
 * <p>Estende {@link CodedException} (antes era {@code RuntimeException} nua) porque sem código não
 * havia entrada de catálogo possível: a exceção caía no handler genérico e voltava como {@code 500
 * / internal_error}. Tenant já semeado é regra de negócio satisfeita — o estado é que proíbe a
 * operação, não um defeito do servidor. Mapeia para {@code 409 / conflict}.
 */
public class TenantAlreadySeededException extends CodedException {

    private final transient UUID tenantId;

    public TenantAlreadySeededException(UUID tenantId) {
        super(
                "acs.tenant.already-seeded",
                "tenant already has caregivers, bootstrap is not available: " + tenantId);
        this.tenantId = tenantId;
        withContext(Map.of("tenantId", tenantId.toString()));
    }

    public UUID getTenantId() {
        return tenantId;
    }
}
