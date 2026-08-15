package com.neuroassist.gest.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.api.dto.TipoGestoRequest;
import com.neuroassist.gest.api.dto.TipoGestoResponse;
import com.neuroassist.gest.api.dto.TransitionRequest;
import com.neuroassist.gest.api.exception.DuplicateTipoGestoException;
import com.neuroassist.gest.api.exception.IllegalTransitionException;
import com.neuroassist.gest.api.exception.InvalidNomeException;
import com.neuroassist.gest.api.exception.TipoGestoNotFoundException;
import com.neuroassist.gest.application.port.out.TenantContextPort;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.application.port.out.TipoGestoTransitionRepositoryPort;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.domain.model.TipoGestoTransition;
import com.neuroassist.gest.domain.service.TipoGestoDomainService;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * Unit tests for TipoGestoService business logic (Fases 3+4 — P2).
 *
 * <p>Covers: register (GEST-006) and transition (GEST-007) use cases, with Mockito stubs for all
 * outbound ports.
 */
@ExtendWith(MockitoExtension.class)
class TipoGestoServiceTest {

    @Mock private TipoGestoRepositoryPort tipoGestoRepositoryPort;

    @Mock private TipoGestoTransitionRepositoryPort tipoGestoTransitionRepositoryPort;

    @Mock private TipoGestoDomainService tipoGestoDomainService;

    @Mock private ApplicationEventPublisher eventPublisher;

    @Mock private TenantContextPort tenantContextPort;

    @InjectMocks private TipoGestoService tipoGestoService;

    // ── register ────────────────────────────────────────────────────────────

    @Test
    void register_novoNome_returnsRegistered() {
        TipoGestoRequest request = new TipoGestoRequest("Piscar");
        UUID id = UUID.randomUUID();
        TipoGesto saved =
                new TipoGesto(
                        id,
                        "Piscar",
                        DetectabilityState.REGISTERED,
                        null,
                        "tenant-pending-p5",
                        Instant.now(),
                        Instant.now(),
                        null,
                        null);

        when(tipoGestoRepositoryPort.existsByNomeIgnoreCaseAndStateNotDeprecated("Piscar"))
                .thenReturn(false);
        when(tipoGestoRepositoryPort.save(any(TipoGesto.class))).thenReturn(saved);

        TipoGestoResponse response = tipoGestoService.register(request);

        assertThat(response.nome()).isEqualTo("Piscar");
        assertThat(response.detectabilityState()).isEqualTo("registered");
        assertThat(response.id()).isEqualTo(id);
        verify(eventPublisher).publishEvent(any(Object.class));
    }

    @Test
    void register_nomeDuplicado_throwsDuplicateTipoGestoException() {
        TipoGestoRequest request = new TipoGestoRequest("Piscar");
        when(tipoGestoRepositoryPort.existsByNomeIgnoreCaseAndStateNotDeprecated("Piscar"))
                .thenReturn(true);

        assertThatThrownBy(() -> tipoGestoService.register(request))
                .isInstanceOf(DuplicateTipoGestoException.class);
    }

    @Test
    void register_nomeVazio_throwsInvalidNomeException() {
        TipoGestoRequest request = new TipoGestoRequest("   ");

        assertThatThrownBy(() -> tipoGestoService.register(request))
                .isInstanceOf(InvalidNomeException.class);
    }

    @Test
    void register_nomeExceedsMaxLength_throwsInvalidNomeException() {
        String longNome = "a".repeat(121);
        TipoGestoRequest request = new TipoGestoRequest(longNome);

        assertThatThrownBy(() -> tipoGestoService.register(request))
                .isInstanceOf(InvalidNomeException.class);
    }

    // ── listAll ──────────────────────────────────────────────────────────────

    @Test
    void listAll_multipleGestos_returnsAll() {
        List<TipoGesto> gestos =
                List.of(
                        new TipoGesto(
                                UUID.randomUUID(),
                                "Piscar",
                                DetectabilityState.SUPPORTED,
                                "ref1",
                                "t1",
                                Instant.now(),
                                Instant.now(),
                                null,
                                null),
                        new TipoGesto(
                                UUID.randomUUID(),
                                "Sorrir",
                                DetectabilityState.REGISTERED,
                                null,
                                "t1",
                                Instant.now(),
                                Instant.now(),
                                null,
                                null));
        when(tipoGestoRepositoryPort.findAllByTenant()).thenReturn(gestos);

        List<TipoGestoResponse> responses = tipoGestoService.listAll();

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).nome()).isEqualTo("Piscar");
        assertThat(responses.get(1).nome()).isEqualTo("Sorrir");
    }

    @Test
    void listAll_empty_returnsEmptyList() {
        when(tipoGestoRepositoryPort.findAllByTenant()).thenReturn(List.of());

        assertThat(tipoGestoService.listAll()).isEmpty();
    }

    // ── findById ─────────────────────────────────────────────────────────────

    @Test
    void findById_existing_returnsResponse() {
        UUID id = UUID.randomUUID();
        TipoGesto gesto =
                new TipoGesto(
                        id,
                        "Piscar",
                        DetectabilityState.REGISTERED,
                        null,
                        "t1",
                        Instant.now(),
                        Instant.now(),
                        null,
                        null);
        when(tipoGestoRepositoryPort.findById(id)).thenReturn(Optional.of(gesto));

        TipoGestoResponse response = tipoGestoService.findById(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.nome()).isEqualTo("Piscar");
    }

    @Test
    void findById_notFound_throwsTipoGestoNotFoundException() {
        UUID id = UUID.randomUUID();
        when(tipoGestoRepositoryPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tipoGestoService.findById(id))
                .isInstanceOf(TipoGestoNotFoundException.class);
    }

    // ── transition ───────────────────────────────────────────────────────────

    @Test
    void transition_validTransition_returnsUpdated() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TipoGesto current =
                new TipoGesto(
                        id,
                        "Piscar",
                        DetectabilityState.REGISTERED,
                        null,
                        "t1",
                        now,
                        now,
                        null,
                        null);
        TipoGesto updated =
                new TipoGesto(
                        id,
                        "Piscar",
                        DetectabilityState.PENDING_TRAINING,
                        null,
                        "t1",
                        now,
                        Instant.now(),
                        null,
                        "system");
        TransitionRequest request =
                new TransitionRequest("PENDING_TRAINING", "system", "ready for training", null);

        when(tipoGestoRepositoryPort.findById(id)).thenReturn(Optional.of(current));
        doNothing().when(tipoGestoDomainService).validateTransition(any(), any());
        when(tipoGestoTransitionRepositoryPort.save(any(TipoGestoTransition.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(tipoGestoRepositoryPort.save(any(TipoGesto.class))).thenReturn(updated);

        TipoGestoResponse response = tipoGestoService.transition(id, request);

        assertThat(response.detectabilityState()).isEqualTo("pending_training");
        // PrimitiveTransitioned event only (not DEPRECATED)
        verify(eventPublisher, times(1)).publishEvent(any(Object.class));
    }

    @Test
    void transition_toDeprecated_emitsTwoEvents() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        TipoGesto current =
                new TipoGesto(
                        id,
                        "Piscar",
                        DetectabilityState.SUPPORTED,
                        "ref1",
                        "t1",
                        now,
                        now,
                        null,
                        null);
        TipoGesto updated =
                new TipoGesto(
                        id,
                        "Piscar",
                        DetectabilityState.DEPRECATED,
                        "ref1",
                        "t1",
                        now,
                        Instant.now(),
                        null,
                        "admin");
        TransitionRequest request = new TransitionRequest("DEPRECATED", "admin", "obsolete", null);

        when(tipoGestoRepositoryPort.findById(id)).thenReturn(Optional.of(current));
        doNothing().when(tipoGestoDomainService).validateTransition(any(), any());
        when(tipoGestoTransitionRepositoryPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(tipoGestoRepositoryPort.save(any())).thenReturn(updated);

        tipoGestoService.transition(id, request);

        // Expect PrimitiveTransitioned + PrimitiveDeprecated = 2 publishEvent calls
        verify(eventPublisher, times(2)).publishEvent(any(Object.class));
    }

    @Test
    void transition_invalidToState_throwsIllegalTransitionException() {
        UUID id = UUID.randomUUID();
        TipoGesto current =
                new TipoGesto(
                        id,
                        "Piscar",
                        DetectabilityState.REGISTERED,
                        null,
                        "t1",
                        Instant.now(),
                        Instant.now(),
                        null,
                        null);
        TransitionRequest request =
                new TransitionRequest("SUPPORTED", "system", "skip steps", null);

        when(tipoGestoRepositoryPort.findById(id)).thenReturn(Optional.of(current));
        doThrow(new IllegalTransitionException("REGISTERED", "SUPPORTED"))
                .when(tipoGestoDomainService)
                .validateTransition(any(), any());

        assertThatThrownBy(() -> tipoGestoService.transition(id, request))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_unknownToStateString_throwsIllegalTransitionException() {
        UUID id = UUID.randomUUID();
        TipoGesto current =
                new TipoGesto(
                        id,
                        "Piscar",
                        DetectabilityState.REGISTERED,
                        null,
                        "t1",
                        Instant.now(),
                        Instant.now(),
                        null,
                        null);
        TransitionRequest request = new TransitionRequest("INVALID_STATE", "system", null, null);

        when(tipoGestoRepositoryPort.findById(id)).thenReturn(Optional.of(current));

        // valueOf throws IllegalArgumentException → service converts to IllegalTransitionException
        assertThatThrownBy(() -> tipoGestoService.transition(id, request))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void transition_notFound_throwsTipoGestoNotFoundException() {
        UUID id = UUID.randomUUID();
        TransitionRequest request = new TransitionRequest("PENDING_TRAINING", "system", null, null);
        when(tipoGestoRepositoryPort.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tipoGestoService.transition(id, request))
                .isInstanceOf(TipoGestoNotFoundException.class);
    }
}
