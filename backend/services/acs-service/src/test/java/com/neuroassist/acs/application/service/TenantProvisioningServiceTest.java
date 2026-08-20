package com.neuroassist.acs.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.application.port.out.HistAuditPort;
import com.neuroassist.acs.application.port.out.MigProvisioningPort;
import com.neuroassist.acs.application.port.out.MigProvisioningPort.MigSchemaResponse;
import com.neuroassist.acs.application.port.out.MigProvisioningPort.MigSchemaStatusResponse;
import com.neuroassist.acs.application.port.out.TenantRepositoryPort;
import com.neuroassist.acs.domain.model.Tenant;
import com.neuroassist.acs.domain.model.TenantStatus;
import com.neuroassist.acs.domain.model.TenantTipo;
import com.neuroassist.acs.shared.error.TenantProvisioningTimeoutException;
import com.neuroassist.acs.shared.error.UpstreamUnavailableException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TenantProvisioningService (ACS-012). Verifies SAGA flows: success, MIG timeout,
 * MIG failure.
 */
class TenantProvisioningServiceTest {

    private TenantRepositoryPort tenantRepository;
    private MigProvisioningPort migProvisioningPort;
    private HistAuditPort histAuditPort;
    private TenantProvisioningService service;

    private static final UUID TENANT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        tenantRepository = mock(TenantRepositoryPort.class);
        migProvisioningPort = mock(MigProvisioningPort.class);
        histAuditPort = mock(HistAuditPort.class);

        service =
                new TenantProvisioningService(
                        tenantRepository,
                        migProvisioningPort,
                        histAuditPort,
                        new SimpleMeterRegistry());
    }

    @Test
    void provision_migReturnsReady_activatesTenant() {
        Tenant draft = buildDraftTenant();

        // MIG responds READY immediately on first poll
        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(migProvisioningPort.requestTenantSchema(eq(TENANT_ID), anyString()))
                .thenReturn(new MigSchemaResponse(TENANT_ID, "t_abc123", "PENDING"));
        when(migProvisioningPort.getTenantSchemaStatus(TENANT_ID))
                .thenReturn(new MigSchemaStatusResponse(TENANT_ID, "READY", 100, null));

        Tenant result = service.provision(draft);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TenantStatus.ACTIVE);
        assertThat(result.schemaNome()).isNotBlank();
        // 3 saves: (1) PROVISIONING null-schema, (2) PROVISIONING with-schema after MIG ok,
        // (3) ACTIVE after poll READY
        verify(tenantRepository, times(3)).save(any());
    }

    @Test
    void provision_migUnavailable_returnsProvisioningState() {
        // New behavior (P7): when MIG is unavailable, return PROVISIONING state for async retry
        // (instead of throwing UpstreamUnavailableException — the caller returns HTTP 202)
        Tenant draft = buildDraftTenant();

        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(migProvisioningPort.requestTenantSchema(any(), anyString()))
                .thenThrow(new RuntimeException("connection refused"));

        Tenant result = service.provision(draft);

        assertThat(result).isNotNull();
        assertThat(result.status()).isEqualTo(TenantStatus.PROVISIONING);
        assertThat(result.schemaNome()).isNull();
        // Only 1 save: PROVISIONING with null schema before MIG attempt
        verify(tenantRepository, times(1)).save(any());
    }

    @Test
    void provision_migReturnsFailed_throwsUpstreamUnavailable() {
        Tenant draft = buildDraftTenant();

        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(migProvisioningPort.requestTenantSchema(any(), anyString()))
                .thenReturn(new MigSchemaResponse(TENANT_ID, "t_abc123", "PENDING"));
        when(migProvisioningPort.getTenantSchemaStatus(TENANT_ID))
                .thenReturn(new MigSchemaStatusResponse(TENANT_ID, "FAILED", 0, null));

        assertThatThrownBy(() -> service.provision(draft))
                .isInstanceOf(UpstreamUnavailableException.class);
    }

    @Test
    void provision_pollingNeverReady_throwsTimeout() {
        Tenant draft = buildDraftTenant();

        when(tenantRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(migProvisioningPort.requestTenantSchema(any(), anyString()))
                .thenReturn(new MigSchemaResponse(TENANT_ID, "t_abc123", "PENDING"));
        // Always returns PROVISIONING — never READY
        when(migProvisioningPort.getTenantSchemaStatus(TENANT_ID))
                .thenReturn(new MigSchemaStatusResponse(TENANT_ID, "PROVISIONING", 50, 10));

        assertThatThrownBy(() -> service.provision(draft))
                .isInstanceOf(TenantProvisioningTimeoutException.class);
    }

    private Tenant buildDraftTenant() {
        return new Tenant(
                TENANT_ID,
                "Clínica Esperança",
                TenantTipo.CLINICA,
                TenantStatus.PROVISIONING,
                null,
                Instant.now());
    }
}
