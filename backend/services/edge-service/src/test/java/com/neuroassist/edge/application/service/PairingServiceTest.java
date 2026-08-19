package com.neuroassist.edge.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

@ExtendWith(MockitoExtension.class)
class PairingServiceTest {

    @Mock private DispositivoEdgeRepositoryPort deviceRepo;
    @Mock private DispositivoEdgeTransitionRepositoryPort transitionRepo;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private EdgeMetrics edgeMetrics;

    private final DispositivoEdgeDomainService domainService = new DispositivoEdgeDomainService();
    private PairingService sut;

    private static final String TENANT = "tenant-test";
    private static final String ACTOR = "op1";

    @BeforeEach
    void setUp() {
        sut =
                new PairingService(
                        deviceRepo, transitionRepo, domainService, eventPublisher, edgeMetrics);
        TenantContextHolder.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private DispositivoEdge device(UUID id, PairingState state, UUID patientId) {
        return new DispositivoEdge(
                id,
                TENANT,
                "label",
                patientId,
                state,
                "1.0",
                null,
                null,
                0L,
                Instant.now(),
                Instant.now(),
                ACTOR,
                ACTOR);
    }

    // ---- pair ----

    @Test
    @DisplayName("pair: registered device → paired with patient")
    void pairRegisteredDevice() {
        UUID deviceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        DispositivoEdge existing = device(deviceId, PairingState.registered, null);
        DispositivoEdge saved = existing.withPaired(patientId, ACTOR);

        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(existing));
        when(deviceRepo.findByPairingStateAndPacienteIdAndTenantId(
                        PairingState.paired, patientId, TENANT))
                .thenReturn(Optional.empty());
        when(deviceRepo.save(any())).thenReturn(saved);
        when(transitionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        DispositivoEdge result = sut.pair(deviceId, patientId, ACTOR);

        assertThat(result.pairingState()).isEqualTo(PairingState.paired);
        assertThat(result.pacienteId()).isEqualTo(patientId);
        verify(transitionRepo).save(any(DispositivoEdgeTransition.class));
    }

    @Test
    @DisplayName("pair: retired device throws device-retired (409)")
    void pairRetiredThrows() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT))
                .thenReturn(Optional.of(device(deviceId, PairingState.retired, null)));

        assertThatThrownBy(() -> sut.pair(deviceId, UUID.randomUUID(), ACTOR))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.device-retired");
    }

    @Test
    @DisplayName("pair: already paired device throws pairing-conflict (409)")
    void pairAlreadyPairedThrows() {
        UUID deviceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT))
                .thenReturn(Optional.of(device(deviceId, PairingState.paired, patientId)));

        assertThatThrownBy(() -> sut.pair(deviceId, patientId, ACTOR))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.pairing-conflict");
    }

    @Test
    @DisplayName("pair: exclusive-pairing violated (patient already has paired device) → 409")
    void pairExclusivePairingViolated() {
        UUID deviceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        UUID otherDeviceId = UUID.randomUUID();

        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT))
                .thenReturn(Optional.of(device(deviceId, PairingState.registered, null)));
        when(deviceRepo.findByPairingStateAndPacienteIdAndTenantId(
                        PairingState.paired, patientId, TENANT))
                .thenReturn(Optional.of(device(otherDeviceId, PairingState.paired, patientId)));

        assertThatThrownBy(() -> sut.pair(deviceId, patientId, ACTOR))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.pairing-conflict");
    }

    // ---- unpair ----

    @Test
    @DisplayName("unpair: paired device → unpaired + audit transition")
    void unpairPairedDevice() {
        UUID deviceId = UUID.randomUUID();
        UUID patientId = UUID.randomUUID();
        DispositivoEdge paired = device(deviceId, PairingState.paired, patientId);
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT)).thenReturn(Optional.of(paired));
        when(deviceRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(transitionRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.unpair(deviceId, "device replacement", ACTOR);

        verify(deviceRepo).save(any());
        verify(transitionRepo).save(any(DispositivoEdgeTransition.class));
    }

    @Test
    @DisplayName("unpair: non-paired device throws illegal-pairing-transition (409)")
    void unpairNonPairedThrows() {
        UUID deviceId = UUID.randomUUID();
        when(deviceRepo.findByIdAndTenantId(deviceId, TENANT))
                .thenReturn(Optional.of(device(deviceId, PairingState.registered, null)));

        assertThatThrownBy(() -> sut.unpair(deviceId, "reason", ACTOR))
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("edge.illegal-pairing-transition");
    }
}
