package com.neuroassist.edge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.edge.application.port.in.HeartbeatUseCase;
import com.neuroassist.edge.application.port.in.ProfilePointerUseCase;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeTransitionRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.metrics.EdgeMetrics;
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
class HeartbeatServiceTest {

    @Mock private DispositivoEdgeRepositoryPort deviceRepo;
    @Mock private DispositivoEdgeTransitionRepositoryPort transitionRepo;
    @Mock private ProfilePointerUseCase profilePointerUseCase;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EdgeMetrics edgeMetrics;

    private final DispositivoEdgeDomainService domainService = new DispositivoEdgeDomainService();
    private HeartbeatService sut;

    private static final String TENANT = "tenant-test";
    private static final long INTERVAL = 30L;
    private static final long STALENESS_FACTOR = 3L;

    @BeforeEach
    void setUp() {
        sut =
                new HeartbeatService(
                        deviceRepo,
                        transitionRepo,
                        profilePointerUseCase,
                        domainService,
                        eventPublisher,
                        edgeMetrics);
        ReflectionTestUtils.setField(sut, "heartbeatIntervalSeconds", INTERVAL);
        ReflectionTestUtils.setField(sut, "stalenessFactor", STALENESS_FACTOR);
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
    @DisplayName("heartbeat: accepted for paired device — stamps server time, returns online")
    void heartbeatStampsServerTime() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge d = device(deviceId, PairingState.paired, Instant.now().minusSeconds(10));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profilePointerUseCase.resolveProfileDrift(any(), any(), any()))
                .thenReturn(new ProfilePointerUseCase.ProfileDriftResult(null, null, true));

        HeartbeatUseCase.HeartbeatResult result = sut.recordHeartbeat(deviceId, "1.4.2", 6L);

        assertThat(result.deviceId()).isEqualTo(deviceId);
        assertThat(result.status()).isEqualTo("online");
        assertThat(result.serverTime()).isNotNull();
        assertThat(result.ultimaSincronizacao()).isNotNull();
        // server time was just stamped — should be very recent
        assertThat(result.ultimaSincronizacao()).isAfter(Instant.now().minusSeconds(5));
    }

    @Test
    @DisplayName("heartbeat: retired device throws device-retired (409)")
    void heartbeatRetiredDeviceThrows() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT))
                .thenReturn(Optional.of(device(deviceId, PairingState.retired, null)));

        assertThatThrownBy(() -> sut.recordHeartbeat(deviceId, "1.0", null))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.device-retired");
    }

    @Test
    @DisplayName("heartbeat: offline→online crossing appends liveness transition")
    void heartbeatOfflineToOnlineAppendsTransition() {
        UUID deviceId = UUID.randomUUID();
        // Device was offline (last sync > 90s ago)
        Instant staleSync = Instant.now().minusSeconds(200);
        DispositivoEdge d = device(deviceId, PairingState.paired, staleSync);
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profilePointerUseCase.resolveProfileDrift(any(), any(), any()))
                .thenReturn(new ProfilePointerUseCase.ProfileDriftResult(null, null, true));

        sut.recordHeartbeat(deviceId, "1.4.2", 5L);

        // Should have saved a liveness transition (offline→online)
        verify(transitionRepo).save(any(DispositivoEdgeTransition.class));
    }

    @Test
    @DisplayName("heartbeat: already online — no liveness transition appended")
    void heartbeatAlreadyOnlineNoTransition() {
        UUID deviceId = UUID.randomUUID();
        // Device was online (last sync 10s ago)
        Instant freshSync = Instant.now().minusSeconds(10);
        DispositivoEdge d = device(deviceId, PairingState.paired, freshSync);
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profilePointerUseCase.resolveProfileDrift(any(), any(), any()))
                .thenReturn(new ProfilePointerUseCase.ProfileDriftResult(null, null, true));

        sut.recordHeartbeat(deviceId, "1.4.2", 5L);

        // No liveness transition since device was already online
        verify(transitionRepo, never()).save(any());
    }

    @Test
    @DisplayName("heartbeat: GEST unreachable — profilePointerDegraded=true, drift unknown")
    void heartbeatGestUnavailableDegraded() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge d = device(deviceId, PairingState.paired, Instant.now().minusSeconds(10));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(profilePointerUseCase.resolveProfileDrift(any(), any(), any()))
                .thenReturn(new ProfilePointerUseCase.ProfileDriftResult(null, null, true));

        HeartbeatUseCase.HeartbeatResult result = sut.recordHeartbeat(deviceId, "1.4.2", 5L);

        assertThat(result.profilePointerDegraded()).isTrue();
        assertThat(result.currentProfileVersion()).isNull();
        assertThat(result.profileDrift()).isNull();
    }

    @Test
    @DisplayName("heartbeat: profile drift detected when running version differs from current")
    void heartbeatProfileDriftDetected() {
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge d = device(deviceId, PairingState.paired, Instant.now().minusSeconds(10));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // running=5, active=7 → drift=true (pre-computed by ProfilePointerService)
        when(profilePointerUseCase.resolveProfileDrift(any(), any(), any()))
                .thenReturn(new ProfilePointerUseCase.ProfileDriftResult(7L, true, false));

        HeartbeatUseCase.HeartbeatResult result =
                sut.recordHeartbeat(deviceId, "1.4.2", 5L); // running=5, current=7

        assertThat(result.profilePointerDegraded()).isFalse();
        assertThat(result.currentProfileVersion()).isEqualTo(7L);
        assertThat(result.profileDrift()).isTrue();
    }

    @Test
    @DisplayName("heartbeat: GEST has no published profile — NOT degraded, drift=false (HB-04)")
    void heartbeatNoActiveProfileIsNotDegraded() {
        // The case the old Optional<Long> port could not express: "GEST answered, and there is no
        // profile yet" collapsed into the same empty value as "GEST did not answer", so a healthy
        // tenant that had published nothing was reported as degraded.
        UUID deviceId = UUID.randomUUID();
        DispositivoEdge d = device(deviceId, PairingState.paired, Instant.now().minusSeconds(10));
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(d));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // noActiveProfile: GEST answered "no profile published yet" → degraded=false, drift=false
        when(profilePointerUseCase.resolveProfileDrift(any(), any(), any()))
                .thenReturn(new ProfilePointerUseCase.ProfileDriftResult(null, false, false));

        HeartbeatUseCase.HeartbeatResult result = sut.recordHeartbeat(deviceId, "1.4.2", null);

        assertThat(result.profilePointerDegraded()).isFalse();
        assertThat(result.currentProfileVersion()).isNull();
        assertThat(result.profileDrift()).isFalse();
    }
}
