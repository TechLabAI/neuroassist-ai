package com.neuroassist.acs.application.service;

import com.neuroassist.acs.application.port.out.HistAuditPort;
import com.neuroassist.acs.application.port.out.HistAuditPort.AuditFact;
import com.neuroassist.acs.application.port.out.MigProvisioningPort;
import com.neuroassist.acs.application.port.out.MigProvisioningPort.MigSchemaResponse;
import com.neuroassist.acs.application.port.out.MigProvisioningPort.MigSchemaStatusResponse;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Tenant;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.shared.error.TenantProvisioningTimeoutException;
import com.neuroassist.acs.shared.error.UpstreamUnavailableException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implements the tenant provisioning SAGA (ACS-012).
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Generate schemaName = "t_" + UUID
 *   <li>Update tenant status = PROVISIONING, schemaNome = schemaName
 *   <li>Call MigProvisioningPort.requestTenantSchema() (timeout 5s, fail-closed)
 *   <li>Poll MigProvisioningPort.getTenantSchemaStatus() until READY (max 30s, exp backoff)
 *   <li>Update tenant status = ACTIVE, schemaNome = confirmed schemaName
 *   <li>Audit via HistAuditPort (fire-and-forget)
 * </ol>
 *
 * <p>RULE-03: @Transactional only here. Resilience4j retry + circuit breaker configured at adapter
 * level (ACS-011/P3).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private static final int MAX_POLL_ATTEMPTS = 10;
    private static final long INITIAL_BACKOFF_MS = 1_000L;
    private static final long MAX_BACKOFF_MS = 5_000L;
    private static final String METRIC_PROVISIONING_DURATION = "acs.tenant.provisioning.duration";
    private static final String OBS_PROVISION = "acs.tenant.provision";

    private final TenantRepositoryPort tenantRepository;
    private final MigProvisioningPort migProvisioningPort;
    private final HistAuditPort histAuditPort;
    private final MeterRegistry meterRegistry;
    private final ObservationRegistry observationRegistry;

    /**
     * Provisions a new tenant schema via MIG service (SAGA, fail-soft on MIG request).
     *
     * <p>If MIG is unavailable during the initial schema request, the tenant is persisted with
     * PROVISIONING status and null schemaNome, and returned immediately for async retry (202). The
     * caller (TenantController) uses the status to decide the HTTP response code.
     *
     * @param tenant the tenant in PROVISIONING status (id and nome must be set)
     * @return the tenant: ACTIVE if provisioning succeeded, PROVISIONING if MIG was unavailable
     * @throws TenantProvisioningTimeoutException if schema does not reach READY within poll limit
     */
    @Transactional
    public Tenant provision(Tenant tenant) {
        return Observation.createNotStarted(OBS_PROVISION, observationRegistry)
                .observe(
                        () -> {
                            Timer.Sample sample = Timer.start(meterRegistry);
                            try {
                                Tenant activated = doProvision(tenant);
                                sample.stop(
                                        meterRegistry.timer(
                                                METRIC_PROVISIONING_DURATION, "status", "success"));
                                return activated;
                            } catch (Exception e) {
                                sample.stop(
                                        meterRegistry.timer(
                                                METRIC_PROVISIONING_DURATION, "status", "failure"));
                                throw e;
                            }
                        });
    }

    private Tenant doProvision(Tenant tenant) {
        log.info("Provisioning tenant id={}", tenant.id());

        // Step 1: persist PROVISIONING status with null schema (schema assigned after MIG confirms)
        Instant criadoEm = tenant.criadoEm() != null ? tenant.criadoEm() : Instant.now();
        Tenant provisioning =
                new Tenant(
                        tenant.id(),
                        tenant.nome(),
                        tenant.tipo(),
                        TenantStatus.PROVISIONING,
                        null,
                        criadoEm);
        tenantRepository.save(provisioning);

        // Step 2: request schema creation from MIG (fail-soft: return PROVISIONING if unavailable)
        String schemaName = "t_" + UUID.randomUUID().toString().replace("-", "");
        MigSchemaResponse migResponse;
        try {
            migResponse = migProvisioningPort.requestTenantSchema(tenant.id(), schemaName);
        } catch (Exception e) {
            // MIG unavailable — tenant stays PROVISIONING with null schema for async retry
            log.error(
                    "MIG schema request failed for tenant={}, returning PROVISIONING for async"
                            + " retry: {}",
                    tenant.id(),
                    e.getMessage());
            return provisioning;
        }

        // Use our generated schema name — MIG confirms provisioning but we own the name
        String confirmedSchema = schemaName;

        // Step 3: update tenant with confirmed schema name
        Tenant withSchema =
                new Tenant(
                        tenant.id(),
                        tenant.nome(),
                        tenant.tipo(),
                        TenantStatus.PROVISIONING,
                        confirmedSchema,
                        criadoEm);
        tenantRepository.save(withSchema);

        // If MIG already reports READY, activate immediately without polling
        if ("READY".equals(migResponse.status())) {
            log.info(
                    "MIG reported READY immediately for tenant={} schema={}",
                    tenant.id(),
                    confirmedSchema);
            Tenant activated =
                    new Tenant(
                            tenant.id(),
                            tenant.nome(),
                            tenant.tipo(),
                            TenantStatus.ACTIVE,
                            confirmedSchema,
                            criadoEm);
            return tenantRepository.save(activated);
        }

        // Step 4: poll until READY
        Tenant activated = pollUntilReady(withSchema);

        // Step 5: audit (fire-and-forget)
        recordProvisioningAudit(activated, "TENANT_PROVISIONED");

        return activated;
    }

    private Tenant pollUntilReady(Tenant provisioning) {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 1; attempt <= MAX_POLL_ATTEMPTS; attempt++) {
            try {
                MigSchemaStatusResponse status =
                        migProvisioningPort.getTenantSchemaStatus(provisioning.id());

                log.debug(
                        "Poll attempt {}/{} for tenant={}: status={}",
                        attempt,
                        MAX_POLL_ATTEMPTS,
                        provisioning.id(),
                        status.status());

                if ("READY".equals(status.status())) {
                    Tenant activated =
                            new Tenant(
                                    provisioning.id(),
                                    provisioning.nome(),
                                    provisioning.tipo(),
                                    TenantStatus.ACTIVE,
                                    provisioning.schemaNome(),
                                    provisioning.criadoEm());
                    return tenantRepository.save(activated);
                }

                if ("FAILED".equals(status.status())) {
                    throw new UpstreamUnavailableException(
                            "mig-service", new RuntimeException("Schema provisioning FAILED"));
                }

                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);

            } catch (UpstreamUnavailableException e) {
                throw e;
            } catch (Exception e) {
                log.warn(
                        "Poll attempt {} failed for tenant={}: {}",
                        attempt,
                        provisioning.id(),
                        e.getMessage());
                sleep(backoffMs);
                backoffMs = Math.min(backoffMs * 2, MAX_BACKOFF_MS);
            }
        }

        log.error(
                "Provisioning timed out for tenant={} after {} attempts",
                provisioning.id(),
                MAX_POLL_ATTEMPTS);
        throw new TenantProvisioningTimeoutException(provisioning.id());
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private void recordProvisioningAudit(Tenant tenant, String fact) {
        try {
            histAuditPort.recordFact(
                    new AuditFact(
                            fact,
                            tenant.id(),
                            null,
                            MDC.get("correlationId"),
                            Instant.now(),
                            Map.of(
                                    "tenantNome",
                                    tenant.nome(),
                                    "schemaNome",
                                    tenant.schemaNome())));
        } catch (Exception e) {
            log.warn("Failed to audit {}: {}", fact, e.getMessage());
        }
    }
}
