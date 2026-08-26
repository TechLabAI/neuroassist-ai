package com.neuroassist.gest.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.api.dto.DetectionProfilePrimitiveResponse;
import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.out.DetectionProfilePrimitiveRepositoryPort;
import com.neuroassist.gest.application.port.out.DetectionProfileRepositoryPort;
import com.neuroassist.gest.application.port.out.TenantContextPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.event.DetectionProfilePublished;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.DetectionProfile;
import com.neuroassist.gest.domain.model.DetectionProfilePrimitive;
import com.neuroassist.gest.domain.model.DetectionThresholdTable;
import com.neuroassist.gest.domain.model.Sensibilidade;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.domain.service.DetectionProfileDomainService;
import com.neuroassist.gest.shared.error.CodedException;
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

    @Mock private TenantContextPort tenantContextPort;

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
                        "v1.1.0",
                        DetectionThresholdTable.defaults(),
                        Instant.now(),
                        null,
                        "tenant-pending-p5");
        DetectionProfilePrimitive primitive =
                new DetectionProfilePrimitive(
                        UUID.randomUUID(),
                        profileId,
                        supportedGesto.id(),
                        supportedGesto.nome(),
                        supportedGesto.engineFeatureRef(),
                        Sensibilidade.media);

        // all gestos includes one non-supported — service must filter to SUPPORTED only
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(List.of(supportedGesto, registered("Outro")));
        when(detectionProfileRepositoryPort.nextVersion()).thenReturn(2L);
        doNothing().when(detectionProfileRepositoryPort).deactivateCurrent();
        when(detectionProfileRepositoryPort.save(any(DetectionProfile.class)))
                .thenReturn(savedProfile);
        when(detectionProfileDomainService.createSnapshot(
                        anyList(), any(UUID.class), nullable(Map.class)))
                .thenReturn(List.of(primitive));
        when(detectionProfilePrimitiveRepositoryPort.saveAll(anyList()))
                .thenReturn(List.of(primitive));

        DetectionProfileResponse response =
                detectionProfileService.publish(
                        new DetectionProfileRequest(DetectionThresholdTable.defaults(), Map.of()));

        assertThat(response.version()).isEqualTo(2L);
        assertThat(response.active()).isTrue();
        assertThat(response.contractVersion()).isEqualTo("v1.1.0");
        assertThat(response.etag()).isEqualTo("\"2\"");

        // Verify protocol steps
        verify(detectionProfileRepositoryPort).deactivateCurrent();
        verify(detectionProfileRepositoryPort).save(any(DetectionProfile.class));
        verify(detectionProfilePrimitiveRepositoryPort).saveAll(anyList());
        verify(eventPublisher).publishEvent(any(DetectionProfilePublished.class));
    }

    @Test
    void publish_noSupportedPrimitives_throwsEmptyProfile() {
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(List.of(registered("Piscar"), registered("Sorrir")));

        assertThatThrownBy(
                        () ->
                                detectionProfileService.publish(
                                        new DetectionProfileRequest(null, null)))
                // `empty-profile` (422), nao IllegalStateException: a regra e de negocio e o
                // GlobalExceptionHandler so a traduz corretamente se ela chegar codificada. Com o
                // throw generico a coordenadora recebia 500 (E2E — gest bddTest).
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("empty-profile");

        verify(detectionProfileRepositoryPort, never()).nextVersion();
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void publish_emptyGestoList_throwsEmptyProfile() {
        when(tipoGestoRepositoryPort.findAllByTenant()).thenReturn(List.of());

        assertThatThrownBy(
                        () ->
                                detectionProfileService.publish(
                                        new DetectionProfileRequest(null, null)))
                // `empty-profile` (422), nao IllegalStateException: a regra e de negocio e o
                // GlobalExceptionHandler so a traduz corretamente se ela chegar codificada. Com o
                // throw generico a coordenadora recebia 500 (E2E — gest bddTest).
                .isInstanceOf(CodedException.class)
                .extracting(e -> ((CodedException) e).code())
                .isEqualTo("empty-profile");
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
                        "v1.1.0",
                        DetectionThresholdTable.defaults(),
                        Instant.now(),
                        null,
                        "tenant-pending-p5");

        when(tipoGestoRepositoryPort.findAllByTenant()).thenReturn(List.of(supportedGesto));
        when(detectionProfileRepositoryPort.nextVersion()).thenReturn(5L);
        doNothing().when(detectionProfileRepositoryPort).deactivateCurrent();
        when(detectionProfileRepositoryPort.save(any())).thenReturn(savedProfile);
        when(detectionProfileDomainService.createSnapshot(
                        anyList(), any(UUID.class), nullable(Map.class)))
                .thenReturn(List.of());
        when(detectionProfilePrimitiveRepositoryPort.saveAll(anyList())).thenReturn(List.of());

        DetectionProfileResponse response =
                detectionProfileService.publish(new DetectionProfileRequest(null, null));

        assertThat(response.etag()).isEqualTo("\"5\"");
    }

    // ── findActive ──────────────────────────────────────────────────────────

    @Test
    void findActive_activeProfileExists_returnsResponse() {
        UUID profileId = UUID.randomUUID();
        DetectionProfile profile =
                new DetectionProfile(
                        profileId,
                        3L,
                        true,
                        "v1.1.0",
                        DetectionThresholdTable.defaults(),
                        Instant.now(),
                        null,
                        "t1");
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

    // ── findByVersion ───────────────────────────────────────────────────────
    //
    // O método devolvia Optional.empty() incondicionalmente, então GET
    // /detection-profile/{version} era 404 para toda versão. Estes testes existem para que a
    // consulta à porta seja obrigatória: um retorno fixo volta a reprovar.

    @Test
    void findByVersion_profileExists_returnsResponseWithPrimitives() {
        UUID profileId = UUID.randomUUID();
        UUID tipoGestoId = UUID.randomUUID();
        DetectionProfile profile =
                new DetectionProfile(
                        profileId,
                        7L,
                        false, // uma versão já desativada continua auditável
                        "v1.1.0",
                        DetectionThresholdTable.defaults(),
                        Instant.now(),
                        null,
                        "t1");
        DetectionProfilePrimitive primitive =
                new DetectionProfilePrimitive(
                        UUID.randomUUID(),
                        profileId,
                        tipoGestoId,
                        "Piscar",
                        "ref-Piscar",
                        Sensibilidade.alta);
        when(detectionProfileRepositoryPort.findByVersion(7L)).thenReturn(Optional.of(profile));
        when(detectionProfilePrimitiveRepositoryPort.findByProfileId(profileId))
                .thenReturn(List.of(primitive));

        Optional<DetectionProfileResponse> response = detectionProfileService.findByVersion(7L);

        assertThat(response).isPresent();
        assertThat(response.get().version()).isEqualTo(7L);
        assertThat(response.get().active()).isFalse();
        assertThat(response.get().thresholds()).isEqualTo(DetectionThresholdTable.defaults());
        // Os primitivos daquela versão, como publicados — sem projeção de paciente.
        assertThat(response.get().primitives()).hasSize(1);
        assertThat(response.get().primitives().get(0).tipoGestoId()).isEqualTo(tipoGestoId);
        assertThat(response.get().primitives().get(0).nome()).isEqualTo("Piscar");
        assertThat(response.get().primitives().get(0).engineFeatureRef()).isEqualTo("ref-Piscar");
        assertThat(response.get().primitives().get(0).sensibilidade())
                .isEqualTo(Sensibilidade.alta);
        assertThat(response.get().primitives().get(0).origem())
                .isEqualTo(DetectionProfilePrimitiveResponse.Origem.PERFIL_EMPRESA);
        // Rota da empresa: não há paciente nem catálogo nesta resposta.
        assertThat(response.get().pacienteId()).isNull();
        assertThat(response.get().catalogoVersao()).isNull();
        assertThat(response.get().catalogoDegradado()).isFalse();
        // ETag só da versão — uma versão publicada é imutável (o composto é do /devices/{id}).
        assertThat(response.get().etag()).isEqualTo("\"7\"");
    }

    @Test
    void findByVersion_unknownVersion_returnsEmpty() {
        when(detectionProfileRepositoryPort.findByVersion(99L)).thenReturn(Optional.empty());

        Optional<DetectionProfileResponse> response = detectionProfileService.findByVersion(99L);

        assertThat(response).isEmpty();
        verify(detectionProfilePrimitiveRepositoryPort, never()).findByProfileId(any());
    }
}
