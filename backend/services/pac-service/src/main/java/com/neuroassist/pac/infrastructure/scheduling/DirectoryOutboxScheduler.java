package com.neuroassist.pac.infrastructure.scheduling;

import com.neuroassist.pac.application.port.in.DirectoryOutboxDrainUseCase;
import com.neuroassist.pac.application.port.out.TenantCatalogPort;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Propagates caregiver-patient links to the ACS directory (E2E-015 / defect C-30).
 *
 * <p>Same shape as {@link HistOutboxScheduler}, and for the same two reasons.
 *
 * <p><b>It iterates tenants explicitly.</b> {@code directory_outbox} lives in a per-tenant schema
 * and a scheduled task has no request to inherit a tenant from. Without binding one, {@code
 * MultiTenantConnectionProvider} leaves the connection on the global schema, where the table does
 * not exist — the sweep would find nothing and report success.
 *
 * <p><b>The tenant is bound before the transaction opens.</b> Hibernate fixes the {@code
 * search_path} when it acquires the connection, so binding inside a {@code @Transactional} method
 * routes nothing. This is the trap documented in CLAUDE.md and paid for in EDGE.
 *
 * <p>One tenant's failure never stops the others: an ACS problem affecting one tenant should not
 * strand every other tenant's links behind it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "neuroassist.directory-outbox.drain.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class DirectoryOutboxScheduler {

    private final TenantCatalogPort tenantCatalogPort;
    private final DirectoryOutboxDrainUseCase drainUseCase;

    @Scheduled(
            initialDelayString = "${neuroassist.directory-outbox.drain.initial-delay-ms:30000}",
            fixedDelayString = "${neuroassist.directory-outbox.drain.interval-ms:60000}")
    public void drainAllTenants() {
        for (String tenantId : tenantCatalogPort.activeTenantIds()) {
            try {
                TenantContextHolder.set(tenantId);
                drainUseCase.drainCurrentTenant();
            } catch (Exception e) {
                log.warn(
                        "Directory outbox drain failed for tenant {}: {}",
                        tenantId,
                        e.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
