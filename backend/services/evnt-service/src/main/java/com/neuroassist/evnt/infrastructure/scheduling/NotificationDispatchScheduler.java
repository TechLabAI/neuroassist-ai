package com.neuroassist.evnt.infrastructure.scheduling;

import com.neuroassist.evnt.application.port.in.NotificationOrchestrationUseCase;
import com.neuroassist.evnt.application.port.out.TenantCatalogPort;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically re-attempts undelivered notification dispatches (defect C-45).
 *
 * <p><b>Why it iterates tenants explicitly.</b> {@code notification_dispatch} lives in a per-tenant
 * schema and a scheduled task has no request to inherit a tenant from; without binding one the
 * connection stays on the global schema, where the table does not exist, and the sweep would find
 * nothing and report success.
 *
 * <p><b>The tenant is bound before the transaction opens</b> — Hibernate fixes the {@code
 * search_path} when it acquires the connection, so binding inside a {@code @Transactional} method
 * routes nothing (CLAUDE.md; the trap paid for in EDGE).
 *
 * <p>The interval is what decides how long a caregiver who stepped away waits for an alarm that
 * found no open session, so it is deliberately short.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        name = "evnt.dispatch-retry.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class NotificationDispatchScheduler {

    private final TenantCatalogPort tenantCatalogPort;
    private final NotificationOrchestrationUseCase orchestrationUseCase;

    @Scheduled(
            initialDelayString = "${evnt.dispatch-retry.initial-delay-ms:30000}",
            fixedDelayString = "${evnt.dispatch-retry.interval-ms:30000}")
    public void sweepAllTenants() {
        for (String tenantId : tenantCatalogPort.activeTenantIds()) {
            try {
                TenantContextHolder.set(tenantId);
                orchestrationUseCase.retrySweep();
            } catch (Exception e) {
                log.warn("Dispatch retry sweep failed for tenant {}: {}", tenantId, e.getMessage());
            } finally {
                TenantContextHolder.clear();
            }
        }
    }
}
