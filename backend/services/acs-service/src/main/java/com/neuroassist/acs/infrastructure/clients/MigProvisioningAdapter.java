package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.port.out.MigProvisioningPort;
import com.neuroassist.acs.shared.error.UpstreamUnavailableException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing MigProvisioningPort via MigProvisioningClient (Feign).
 *
 * <p>Fail-closed: any Feign failure propagates as UpstreamUnavailableException (502). Full Feign
 * method implementations deferred to ACS-009 (P3) when the MIG contract is stable. Circuit breaker
 * / retry configured externally via Resilience4j (ACS-012/P2 config).
 *
 * <p>RULE-06: @FeignClient only in infrastructure.clients (MigProvisioningClient).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigProvisioningAdapter implements MigProvisioningPort {

    private final MigProvisioningClient migProvisioningClient;

    /**
     * Requests tenant schema creation from mig-service. Fail-closed: throws
     * UpstreamUnavailableException if MIG is unavailable.
     */
    @Override
    public MigSchemaResponse requestTenantSchema(UUID tenantId, String schemaName) {
        try {
            // TODO (ACS-009/P3): call migProvisioningClient.requestSchema(tenantId, schemaName)
            // Stub: return PENDING response for P2 (real Feign call in P3)
            log.info("MIG schema request (stub): tenantId={} schemaName={}", tenantId, schemaName);
            return new MigSchemaResponse(tenantId, schemaName, "PENDING");
        } catch (Exception e) {
            log.error(
                    "MIG requestTenantSchema failed: tenantId={} error={}",
                    tenantId,
                    e.getMessage());
            throw new UpstreamUnavailableException("mig-service", e);
        }
    }

    /**
     * Polls the provisioning status of a tenant schema. Fail-closed: throws
     * UpstreamUnavailableException if MIG is unavailable.
     */
    @Override
    public MigSchemaStatusResponse getTenantSchemaStatus(UUID tenantId) {
        try {
            // TODO (ACS-009/P3): call migProvisioningClient.getSchemaStatus(tenantId)
            // Stub: return READY for P2 (real Feign call in P3)
            log.debug("MIG schema status poll (stub): tenantId={} → READY", tenantId);
            return new MigSchemaStatusResponse(tenantId, "READY", 100, null);
        } catch (Exception e) {
            log.error(
                    "MIG getTenantSchemaStatus failed: tenantId={} error={}",
                    tenantId,
                    e.getMessage());
            throw new UpstreamUnavailableException("mig-service", e);
        }
    }
}
