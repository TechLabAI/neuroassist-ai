package com.neuroassist.mig.application.port.in;

import com.neuroassist.mig.domain.model.ProvisioningStatus;
import com.neuroassist.mig.domain.model.TenantSchemaProvisioning;
import java.util.UUID;

/**
 * Inbound port — tenant schema provisioning (E2E-016, ADR-005, plan-MIG P4).
 *
 * <p>Synchronous by design (plan-MIG §0: "provisionamento de tenant deterministico e idempotente …
 * invocado sincronamente por ACS"). acs-service blocks on the answer and only then flips the tenant
 * to ACTIVE, so a schema that does not exist can never be reported as usable.
 */
public interface ProvisionTenantSchemaUseCase {

    /**
     * Creates the schema if absent and migrates it to head. Idempotent: a tenant already at head
     * returns {@link ProvisioningStatus#READY} with no applied versions.
     *
     * @param tenantId tenant being provisioned
     * @param schema schema name — <b>supplied by acs-service, never derived here</b>. The tenant
     *     identifier is not the schema name; deriving one from the other is the defect this
     *     platform already paid for (see CLAUDE.md, "tenant id ≠ schema name").
     */
    TenantSchemaProvisioning provision(UUID tenantId, String schema);

    /**
     * Current provisioning status of a tenant, resolved through the global tenant catalogue.
     *
     * <p>Exists for acs-service's poll path. That path is not exercised while provisioning stays
     * synchronous — which is exactly why it must not lie: an endpoint whose only caller is a
     * fallback is the one nobody notices is broken.
     */
    ProvisioningStatus statusOf(UUID tenantId);
}
