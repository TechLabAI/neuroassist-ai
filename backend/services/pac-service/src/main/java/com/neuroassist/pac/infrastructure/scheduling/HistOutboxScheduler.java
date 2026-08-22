package com.neuroassist.pac.infrastructure.scheduling;

import com.neuroassist.pac.application.port.in.HistOutboxDrainUseCase;
import com.neuroassist.pac.application.port.out.TenantCatalogPort;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically redelivers audit facts stranded in the outbox (defect C-44).
 *
 * <p><b>Why it iterates tenants explicitly.</b> Every table pac-service owns lives in a per-tenant
 * schema, and a scheduled task has no request to inherit a tenant from. Without binding one, {@code
 * MultiTenantConnectionProvider} leaves the connection on the global schema, where {@code
 * hist_outbox} does not exist — the sweeper would find nothing and report success.
 *
 * <p><b>The tenant is bound before the transaction opens.</b> Hibernate fixes the {@code
 * search_path} when it acquires the connection, so binding inside a {@code @Transactional} method
 * routes nothing (the trap documented in CLAUDE.md and paid for in EDGE).
 *
 * <p>One tenant's failure never stops the others: an outage that affects one schema should not
 * strand every other tenant's facts behind it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "neuroassist.hist-outbox.drain.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HistOutboxScheduler {

    private final TenantCatalogPort tenantCatalogPort;
    private final HistOutboxDrainUseCase drainUseCase;

    @Scheduled(
            initialDelayString = "${neuroassist.hist-outbox.drain.initial-delay-ms:30000}",
            fixedDelayString = "${neuroassist.hist-outbox.drain.interval-ms:60000}")
    public void drainAllTenants() {
        for (String tenantId : tenantCatalogPort.activeTenantIds()) {
            try {
                TenantContextHolder.set(tenantId);
                drainUseCase.drainCurrentTenant();
            } catch (Exception e) {
                log.warn("Outbox drain failed for tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
