package com.neuroassist.acs.infrastructure.clients;

import com.neuroassist.acs.application.port.out.MigProvisioningPort;
import com.neuroassist.acs.shared.error.UpstreamUnavailableException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing {@link MigProvisioningPort} over {@link MigProvisioningClient} (E2E-016).
 *
 * <p><b>What this replaced.</b> Both methods used to return literals — {@code PENDING} from the
 * request, {@code READY} from the poll — with a {@code // TODO (ACS-009/P3)} where the call
 * belonged. Provisioning a tenant created no schema at all: {@code TenantProvisioningService} saw
 * READY, flipped the tenant to ACTIVE, and recorded a {@code schema_nome} pointing at nothing. All
 * six E2E sessions ran against the seeded {@code t_emp_dev}, so the path was never exercised and
 * the module gate stayed green throughout.
 *
 * <p><b>Fail-closed, and it must stay that way.</b> Every failure becomes {@code
 * UpstreamUnavailableException}, which {@code TenantProvisioningService} turns into a tenant left
 * in PROVISIONING for retry. The one thing this adapter must never do again is answer READY without
 * having asked anybody.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MigProvisioningAdapter implements MigProvisioningPort {

    private final MigProvisioningClient migProvisioningClient;

    @Override
    public MigSchemaResponse requestTenantSchema(UUID tenantId, String schemaName) {
        try {
            MigProvisioningClient.ProvisionTenantResponse response =
                    migProvisioningClient.requestSchema(
                            tenantId, new MigProvisioningClient.ProvisionTenantRequest(schemaName));

            log.info(
                    "MIG provisioned tenant={} schema={} status={} applied={} version={}",
                    tenantId,
                    response.schema(),
                    response.status(),
                    response.appliedVersions() == null ? 0 : response.appliedVersions().size(),
                    response.currentVersion());

            // The schema name comes back from the response, not from the request: if MIG ever
            // resolves a different one, the tenant row must record what actually exists rather
            // than what was asked for.
            return new MigSchemaResponse(tenantId, response.schema(), response.status());

        } catch (Exception e) {
            log.error(
                    "MIG requestTenantSchema failed: tenantId={} schema={} error={}",
                    tenantId,
                    schemaName,
                    e.getMessage());
            throw new UpstreamUnavailableException("mig-service", e);
        }
    }

    @Override
    public MigSchemaStatusResponse getTenantSchemaStatus(UUID tenantId) {
        try {
            MigProvisioningClient.TenantSchemaStatusResponse response =
                    migProvisioningClient.getSchemaStatus(tenantId);
            log.debug("MIG schema status tenant={} status={}", tenantId, response.status());
            return new MigSchemaStatusResponse(
                    tenantId,
                    response.status(),
                    response.percentual(),
                    response.estimatedSeconds());

        } catch (Exception e) {
            log.error(
                    "MIG getTenantSchemaStatus failed: tenantId={} error={}",
                    tenantId,
                    e.getMessage());
            throw new UpstreamUnavailableException("mig-service", e);
        }
    }
}
