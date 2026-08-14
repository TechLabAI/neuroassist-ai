package com.neuroassist.gest.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.application.port.out.GestObservabilityPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.event.DetectionProfilePublished;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.domain.service.DetectionProfileDomainService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for DetectionProfileService (Fase 7 — P2, GEST-009).
 *
 * <p>Tests publish() and findActive() with Mockito stubs for all outbound ports.
 */
@ExtendWith(MockitoExtension.class)
class DetectionProfileServiceTest {

    @Mock private DetectionProfileRepositoryPort detectionProfileRepositoryPort;

    @Mock private DetectionProfilePrimitiveRepositoryPort detectionProfilePrimitiveRepositoryPort;

    @Mock private TipoGestoRepositoryPort tipoGestoRepositoryPort;

    @Mock private DetectionProfileDomainService detectionProfileDomainService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private GestObservabilityPort observabilityPort;

    @InjectMocks private DetectionProfileService detectionProfileService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TipoGesto supported(String nome) {
        return new TipoGesto(
                UUID.randomUUID(),
                nome,
                DetectabilityState.SUPPORTED,
                "ref-" + nome,
                "t1",
                Instant.now(),
                Instant.now(),
                null,
                null);
    }

    private static TipoGesto registered(String nome) {
        return new TipoGesto(
                UUID.randomUUID(),
                nome,
                DetectabilityState.REGISTERED,
                null,
                "t1",
                Instant.now(),
                Instant.now(),
                null,
                null);
    }

    // ── publish ──────────────────────────────────────────────────────────────

    @Test
    void publish_withSupportedPrimitives_publishesAndReturnsResponse() {
        TipoGesto supportedGesto = supported("Piscar");
        UUID profileId = UUID.randomUUID();
        DetectionProfile savedProfile =
                new DetectionProfile(
                        profileId,
                        2L,
                        true,
                        "v1.0.0",
                        Map.of(),
                        Instant.now(),
                        null,
                        "tenant-pending-p5");
        DetectionProfilePrimitive primitive =
                new DetectionProfilePrimitive(
                        UUID.randomUUID(),
                        profileId,
                        supportedGesto.id(),
                        supportedGesto.nome(),
                        supportedGesto.engineFeatureRef());

        // all gestos includes one non-supported — service must filter to SUPPORTED only
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(List.of(supportedGesto, registered("Outro")));
        when(detectionProfileRepositoryPort.nextVersion()).thenReturn(2L);
        doNothing().when(detectionProfileRepositoryPort).deactivateCurrent();
        when(detectionProfileRepositoryPort.save(any(DetectionProfile.class)))
                .thenReturn(savedProfile);
        when(detectionProfileDomainService.createSnapshot(anyList(), any(UUID.class)))
                .thenReturn(List.of(primitive));
        when(detectionProfilePrimitiveRepositoryPort.saveAll(anyList()))
                .thenReturn(List.of(primitive));

        DetectionProfileResponse response =
                detectionProfileService.publish(
                        new DetectionProfileRequest(Map.of("confidenceThreshold", 0.8)));

        assertThat(response.version()).isEqualTo(2L);
        assertThat(response.active()).isTrue();
        assertThat(response.contractVersion()).isEqualTo("v1.0.0");
        assertThat(response.etag()).isEqualTo("\"2\"");

        // Verify protocol steps
        verify(detectionProfileRepositoryPort).deactivateCurrent();
        verify(detectionProfileRepositoryPort).save(any(DetectionProfile.class));
        verify(detectionProfilePrimitiveRepositoryPort).saveAll(anyList());
        verify(eventPublisher).publishEvent(any(DetectionProfilePublished.class));
    }

    @Test
    void publish_noSupportedPrimitives_throwsIllegalStateException() {
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(List.of(registered("Piscar"), registered("Sorrir")));

        assertThatThrownBy(
                        () ->
                                detectionProfileService.publish(
                                        new DetectionProfileRequest(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("no supported primitives");

        verify(detectionProfileRepositoryPort, never()).nextVersion();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publish_emptyGestoList_throwsIllegalStateException() {
        when(tipoGestoRepositoryPort.findAllByTenant()).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                detectionProfileService.publish(
                                        new DetectionProfileRequest(Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("no supported primitives");
    }

    @Test
    void publish_etag_isVersionWrappedInQuotes() {
        TipoGesto supportedGesto = supported("Piscar");
        UUID profileId = UUID.randomUUID();
        DetectionProfile savedProfile =
                new DetectionProfile(
                        profileId,
                        5L,
                        true,
                        "v1.0.0",
                        Map.of(),
                        Instant.now(),
                        null,
                        "tenant-pending-p5");

        when(tipoGestoRepositoryPort.findAllByTenant()).thenReturn(List.of(supportedGesto));
        when(detectionProfileRepositoryPort.nextVersion()).thenReturn(5L);
        doNothing().when(detectionProfileRepositoryPort).deactivateCurrent();
        when(detectionProfileRepositoryPort.save(any())).thenReturn(savedProfile);
        when(detectionProfileDomainService.createSnapshot(anyList(), any(UUID.class)))
                .thenReturn(List.of());
        when(detectionProfilePrimitiveRepositoryPort.saveAll(anyList())).thenReturn(List.of());

        DetectionProfileResponse response =
                detectionProfileService.publish(new DetectionProfileRequest(Map.of()));

        assertThat(response.etag()).isEqualTo("\"5\"");
    }

    // ── findActive ──────────────────────────────────────────────────────────

    @Test
    void findActive_activeProfileExists_returnsResponse() {
        UUID profileId = UUID.randomUUID();
        DetectionProfile profile =
                new DetectionProfile(
                        profileId, 3L, true, "v1.0.0", Map.of(), Instant.now(), null, "t1");
        when(detectionProfileRepositoryPort.findActive()).thenReturn(Optional.of(profile));

        Optional<DetectionProfileResponse> response = detectionProfileService.findActive();

        assertThat(response).isPresent();
        assertThat(response.get().version()).isEqualTo(3L);
        assertThat(response.get().active()).isTrue();
        assertThat(response.get().etag()).isEqualTo("\"3\"");
    }

    @Test
    void findActive_noActiveProfile_returnsEmpty() {
        when(detectionProfileRepositoryPort.findActive()).thenReturn(Optional.empty());

        Optional<DetectionProfileResponse> response = detectionProfileService.findActive();

        assertThat(response).isEmpty();
    }
}
