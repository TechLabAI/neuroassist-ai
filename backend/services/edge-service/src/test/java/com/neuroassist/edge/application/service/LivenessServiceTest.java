package com.neuroassist.edge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.neuroassist.edge.application.port.in.LivenessUseCase;
import com.neuroassist.edge.application.port.in.ProfilePointerUseCase;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class LivenessServiceTest {

    @Mock private DispositivoEdgeRepositoryPort deviceRepo;
    @Mock private ProfilePointerUseCase profilePointerUseCase;
    @Mock private ApplicationEventPublisher eventPublisher;

    private final DispositivoEdgeDomainService domainService = new DispositivoEdgeDomainService();
    private LivenessService sut;

    private static final String TENANT = "tenant-test";

    @BeforeEach
    void setUp() {
        sut = new LivenessService(deviceRepo, profilePointerUseCase, domainService, eventPublisher);
        ReflectionTestUtils.setField(sut, "heartbeatIntervalSeconds", 30L);
        ReflectionTestUtils.setField(sut, "stalenessFactor", 3L);
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private DispositivoEdge device(UUID id, PairingState state, Instant lastSync) {
        return new DispositivoEdge(
                id,
                TENANT,
                "label",
                UUID.randomUUID(),
                state,
                "1.0",
                lastSync,
                5L,
                0L,
                Instant.now(),
                Instant.now(),
                "op",
                "op");
    }

    @Test
    @DisplayName("evaluate: online device returns 'online' with serverTime")
    void evaluateOnlineDevice() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge d = device(deviceId, PairingState.paired, Instant.now().minusSeconds(10));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));

        LivenessUseCase.LivenessResult result = sut.evaluate(deviceId);

        assertThat(result.status()).isEqualTo("online");
        assertThat(result.serverTime()).isNotNull();
        assertThat(result.stalenessThresholdSeconds()).isEqualTo(90L);
        assertThat(result.eTag()).isNotBlank();
    }

    @Test
    @DisplayName("evaluate: stale device returns 'offline'")
    void evaluateStaleDevice() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge d = device(deviceId, PairingState.paired, Instant.now().minusSeconds(200));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));

        LivenessUseCase.LivenessResult result = sut.evaluate(deviceId);

        assertThat(result.status()).isEqualTo("offline");
    }

    @Test
    @DisplayName("evaluate: unpaired device returns 'n/a'")
    void evaluateUnpairedDevice() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge d = device(deviceId, PairingState.unpaired, Instant.now().minusSeconds(10));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));

        LivenessUseCase.LivenessResult result = sut.evaluate(deviceId);

        assertThat(result.status()).isEqualTo("n/a");
    }

    @Test
    @DisplayName("evaluate: device not found throws device-not-found (404)")
    void evaluateNotFoundThrows() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.evaluate(deviceId))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.device-not-found");
    }

    @Test
    @DisplayName("evaluate: ETag differs between online and offline status buckets")
    void evaluateETagChangesOnStatusCrossing() {
        UUID deviceId = UUID.randomUUID();

        // Online device
        DispositivoEdge online =
                device(deviceId, PairingState.paired, Instant.now().minusSeconds(10));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(online));
        String eTagOnline = sut.evaluate(deviceId).eTag();

        // Stale device (same id)
        DispositivoEdge offline =
                device(deviceId, PairingState.paired, Instant.now().minusSeconds(200));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(offline));
        String eTagOffline = sut.evaluate(deviceId).eTag();

        // ETag must differ for a status crossing even without a new heartbeat
        assertThat(eTagOnline).isNotEqualTo(eTagOffline);
    }

    @Test
    @DisplayName("evaluateForPatient: no paired device throws patient-not-paired (404)")
    void evaluateForPatientNoPairedDeviceThrows() {
        UUID patientId = UUID.randomUUID();
        when(deviceRepo.findByPairingStateAndPacienteIdAndTenantId(any(), any(), any()))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.evaluateForPatient(patientId))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.patient-not-paired");
    }

    @Test
    @DisplayName("resolveTenant: returns only deviceId and tenantRef (no patient/liveness data)")
    void resolveTenantReturnsMinimalData() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge d = device(deviceId, PairingState.paired, Instant.now().minusSeconds(10));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));

        LivenessUseCase.TenantResult result = sut.resolveTenant(deviceId);

        assertThat(result.deviceId()).isEqualTo(deviceId);
        assertThat(result.tenantRef()).isEqualTo(TENANT);
    }

    @Test
    @DisplayName("resolveTenant: retired device returns 404 (not tenant mismatch)")
    void resolveTenantRetiredReturns404() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge retired = device(deviceId, PairingState.retired, null);
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(retired));

        assertThatThrownBy(() -> sut.resolveTenant(deviceId))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.device-not-found");
    }
}
