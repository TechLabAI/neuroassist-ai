package com.neuroassist.evnt.infrastructure.scheduling;

import com.neuroassist.evnt.application.port.in.HistOutboxDrainUseCase;
import com.neuroassist.evnt.application.port.out.TenantCatalogPort;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Redelivers stranded audit facts (E2E-017). Same shape as {@link NotificationDispatchScheduler},
 * for the same two reasons.
 *
 * <p><b>It iterates tenants explicitly.</b> {@code hist_outbox} lives in a per-tenant schema and a
 * scheduled task has no request to inherit a tenant from; without binding one the connection stays
 * on the global schema, where the table does not exist, and the sweep would find nothing and report
 * success.
 *
 * <p><b>The tenant is bound before the transaction opens</b> — Hibernate fixes the {@code
 * search_path} when it acquires the connection, so binding inside a {@code @Transactional} method
 * routes nothing (CLAUDE.md; the trap paid for in EDGE).
 *
 * <p>One tenant's failure never stops the others: an outage affecting one schema should not strand
 * every other tenant's facts behind it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "evnt.hist-outbox.drain.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class HistOutboxScheduler {

    private final TenantCatalogPort tenantCatalogPort;
    private final HistOutboxDrainUseCase drainUseCase;

    @Scheduled(
            initialDelayString = "${evnt.hist-outbox.drain.initial-delay-ms:30000}",
            fixedDelayString = "${evnt.hist-outbox.drain.interval-ms:60000}")
    public void drainAllTenants() {
        for (String tenantId : tenantCatalogPort.activeTenantIds()) {
            try {
                TenantContextHolder.set(tenantId);
                drainUseCase.drainCurrentTenant();
            } catch (Exception e) {
                log.warn("Audit outbox drain failed for tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
