package com.neuroassist.gest.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.neuroassist.gest.api.dto.EligibilityRequest;
import com.neuroassist.gest.api.dto.EligibilityResponse;
import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.DetectabilityState;
import com.neuroassist.gest.domain.model.TipoGesto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for EligibilityService.check() (Fase 5 — P2, GEST-008).
 *
 * <p>ADR-006 invariant: EMERGENCIA requires ALL primitives to be SUPPORTED.
 */
@ExtendWith(MockitoExtension.class)
class EligibilityServiceTest {

    @Mock private TipoGestoRepositoryPort tipoGestoRepositoryPort;

    @InjectMocks private EligibilityService eligibilityService;

    // ── helpers ──────────────────────────────────────────────────────────────

    private static TipoGesto gesto(String nome, DetectabilityState state) {
        String ref = state == DetectabilityState.SUPPORTED ? "ref-" + nome : null;
        return new TipoGesto(
                UUID.randomUUID(),
                nome,
                state,
                ref,
                "t1",
                Instant.now(),
                Instant.now(),
                null,
                null);
    }

    // ── EMERGENCIA scenarios ──────────────────────────────────────────────────

    @Test
    void check_emergencia_allSupported_returnsEligible() {
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(
                        List.of(
                                gesto("Piscar", DetectabilityState.SUPPORTED),
                                gesto("Sorrir", DetectabilityState.SUPPORTED)));

        EligibilityResponse response =
                eligibilityService.check(new EligibilityRequest("EMERGENCIA"));

        assertThat(response.eligible()).isTrue();
        assertThat(response.missingStates()).isEmpty();
    }

    @Test
    void check_emergencia_onePendingTraining_returnsIneligible() {
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(
                        List.of(
                                gesto("Piscar", DetectabilityState.SUPPORTED),
                                gesto("Sorrir", DetectabilityState.PENDING_TRAINING)));

        EligibilityResponse response =
                eligibilityService.check(new EligibilityRequest("EMERGENCIA"));

        assertThat(response.eligible()).isFalse();
        assertThat(response.missingStates()).hasSize(1);
        assertThat(response.missingStates().get(0)).contains("Sorrir");
        assertThat(response.missingStates().get(0)).contains("PENDING_TRAINING");
    }

    @Test
    void check_emergencia_allRegistered_returnsIneligible() {
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(
                        List.of(
                                gesto("Piscar", DetectabilityState.REGISTERED),
                                gesto("Sorrir", DetectabilityState.REGISTERED)));

        EligibilityResponse response =
                eligibilityService.check(new EligibilityRequest("EMERGENCIA"));

        assertThat(response.eligible()).isFalse();
        assertThat(response.missingStates()).hasSize(2);
    }

    @Test
    void check_emergencia_emptyPrimitives_returnsEligible() {
        // Vacuously true: no primitives means nothing is blocking (empty-list scenario)
        when(tipoGestoRepositoryPort.findAllByTenant()).thenReturn(List.of());

        EligibilityResponse response =
                eligibilityService.check(new EligibilityRequest("EMERGENCIA"));

        assertThat(response.eligible()).isTrue();
        assertThat(response.missingStates()).isEmpty();
    }

    @Test
    void check_emergencia_caseInsensitive_recognized() {
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(List.of(gesto("Piscar", DetectabilityState.SUPPORTED)));

        EligibilityResponse response =
                eligibilityService.check(new EligibilityRequest("emergencia"));

        assertThat(response.eligible()).isTrue();
    }

    // ── Non-EMERGENCIA scenarios ──────────────────────────────────────────────

    @Test
    void check_rotina_alwaysEligible_noPortCall() {
        EligibilityResponse response = eligibilityService.check(new EligibilityRequest("ROTINA"));

        assertThat(response.eligible()).isTrue();
        assertThat(response.missingStates()).isEmpty();
        verifyNoInteractions(tipoGestoRepositoryPort);
    }

    @Test
    void check_qualquerUrgencia_alwaysEligible() {
        EligibilityResponse response =
                eligibilityService.check(new EligibilityRequest("QUALQUER_NIVEL"));

        assertThat(response.eligible()).isTrue();
        verifyNoInteractions(tipoGestoRepositoryPort);
    }

    @Test
    void check_emergencia_missingStatesContainNomeAndState() {
        when(tipoGestoRepositoryPort.findAllByTenant())
                .thenReturn(List.of(gesto("Acenar", DetectabilityState.DEPRECATED)));

        EligibilityResponse response =
                eligibilityService.check(new EligibilityRequest("EMERGENCIA"));

        assertThat(response.eligible()).isFalse();
        assertThat(response.missingStates().get(0)).contains("Acenar").contains("DEPRECATED");
    }
}
