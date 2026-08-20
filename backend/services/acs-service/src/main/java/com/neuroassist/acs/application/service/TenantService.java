package com.neuroassist.acs.application.service;

import com.neuroassist.acs.api.dto.TenantRequest;
import com.neuroassist.acs.api.dto.TenantResponse;
import com.neuroassist.acs.application.port.in.TenantUseCase;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Tenant;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.domain.model.TenantTipo;
import com.neuroassist.acs.shared.error.TenantNotFoundException;
import com.neuroassist.acs.shared.error.TenantSuspendedException;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tenant CRUD use cases (ACS-013). createTenant delegates to TenantProvisioningService for the MIG
 * SAGA.
 *
 * <p>RULE-03: @Transactional only here.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class TenantService implements TenantUseCase {

    private final TenantRepositoryPort tenantRepository;
    private final TenantProvisioningService provisioningService;

    /**
     * Creates a new tenant and triggers MIG provisioning SAGA.
     *
     * @param request tenant creation data
     * @return TenantResponse once persisted (status=PROVISIONING initially)
     */
    @Override
    public TenantResponse createTenant(TenantRequest request) {
        TenantTipo tipo = TenantTipo.valueOf(request.tipo().toUpperCase());

        Tenant draft =
                new Tenant(
                        UUID.randomUUID(),
                        request.nome(),
                        tipo,
                        TenantStatus.PROVISIONING,
                        null,
                        Instant.now());

        log.info("Creating tenant nome={} tipo={}", draft.nome(), draft.tipo());
        Tenant activated = provisioningService.provision(draft);

        return toResponse(activated);
    }

    /**
     * Retrieves a tenant by UUID.
     *
     * @param tenantId the tenant UUID
     * @return TenantResponse
     * @throws TenantNotFoundException if tenant does not exist
     */
    @Override
    @Transactional(readOnly = true)
    public TenantResponse findById(UUID tenantId) {
        return tenantRepository
                .findById(tenantId)
                .map(t -> {
                    // Data integrity guard: ACTIVE tenant must always have a schema_nome
                    if (t.status() == TenantStatus.ACTIVE && t.schemaNome() == null) {
                        log.error(
                                "DATA DEFECT: ACTIVE tenant {} has null schema_nome — violates ck_tenant_active_needs_schema",
                                t.id());
                        throw new IllegalStateException(
                                "Data defect: active tenant has null schema_nome: " + t.id());
                    }
                    return toResponse(t);
                })
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
    }

    /**
     * Retries provisioning for a tenant stuck in PROVISIONING status.
     *
     * <p>Status routing:
     *
     * <ul>
     *   <li>SUSPENDED → throws TenantSuspendedException (409)
     *   <li>ACTIVE → returns current tenant as-is (no-op, no MIG call)
     *   <li>PROVISIONING → re-triggers MIG provisioning SAGA
     * </ul>
     *
     * @param tenantId the tenant UUID
     * @return updated TenantResponse
     */
    @Override
    public TenantResponse retryProvisioning(UUID tenantId) {
        Tenant existing =
                tenantRepository
                        .findById(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(tenantId));

        log.info(
                "Retry provisioning requested for tenant id={} status={}",
                tenantId,
                existing.status());

        if (existing.status() == TenantStatus.SUSPENDED) {
            log.warn("Retry blocked: tenant id={} is SUSPENDED", tenantId);
            throw new TenantSuspendedException(tenantId);
        }

        if (existing.status() == TenantStatus.ACTIVE) {
            log.info("Tenant id={} is already ACTIVE, no retry needed", tenantId);
            return toResponse(existing);
        }

        // PROVISIONING — re-trigger MIG saga
        Tenant updated = provisioningService.provision(existing);
        return toResponse(updated);
    }

    private TenantResponse toResponse(Tenant tenant) {
        return new TenantResponse(
                tenant.id(),
                tenant.nome(),
                tenant.tipo() != null ? tenant.tipo().name() : null,
                tenant.status() != null ? tenant.status().name() : null,
                tenant.schemaNome(),
                tenant.criadoEm());
    }
}
