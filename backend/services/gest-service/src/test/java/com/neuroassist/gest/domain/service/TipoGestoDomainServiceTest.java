package com.neuroassist.gest.domain.service;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuroassist.gest.api.exception.IllegalTransitionException;
import com.neuroassist.gest.domain.model.DetectabilityState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for TipoGestoDomainService ADR-006 state-machine invariants (Fase 1 — P2).
 *
 * <p>4 allowed arcs: REGISTERED → PENDING_TRAINING | PENDING_TRAINING → SUPPORTED | DEPRECATED
 * SUPPORTED → DEPRECATED
 *
 * <p>All other arcs are illegal. DEPRECATED is a terminal state — no exit.
 */
class TipoGestoDomainServiceTest {

    private TipoGestoDomainService domainService;

    @BeforeEach
    void setUp() {
        domainService = new TipoGestoDomainService();
    }

    // ── Allowed transitions (ADR-006) ──────────────────────────────────────

    @Test
    void validateTransition_registeredToPendingTraining_allowed() {
        assertThatNoException()
                .isThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.REGISTERED,
                                        DetectabilityState.PENDING_TRAINING));
    }

    @Test
    void validateTransition_registeredToDeprecated_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.REGISTERED,
                                        DetectabilityState.DEPRECATED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_pendingTrainingToSupported_allowed() {
        assertThatNoException()
                .isThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.PENDING_TRAINING,
                                        DetectabilityState.SUPPORTED));
    }

    @Test
    void validateTransition_pendingTrainingToDeprecated_allowed() {
        assertThatNoException()
                .isThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.PENDING_TRAINING,
                                        DetectabilityState.DEPRECATED));
    }

    @Test
    void validateTransition_supportedToDeprecated_allowed() {
        assertThatNoException()
                .isThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.SUPPORTED,
                                        DetectabilityState.DEPRECATED));
    }

    // ── Illegal transitions ────────────────────────────────────────────────

    @Test
    void validateTransition_registeredToSupported_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.REGISTERED,
                                        DetectabilityState.SUPPORTED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_registeredToRegistered_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.REGISTERED,
                                        DetectabilityState.REGISTERED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_pendingTrainingToRegistered_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.PENDING_TRAINING,
                                        DetectabilityState.REGISTERED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_pendingTrainingToPendingTraining_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.PENDING_TRAINING,
                                        DetectabilityState.PENDING_TRAINING))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_supportedToRegistered_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.SUPPORTED,
                                        DetectabilityState.REGISTERED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_supportedToPendingTraining_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.SUPPORTED,
                                        DetectabilityState.PENDING_TRAINING))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_deprecatedToRegistered_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.DEPRECATED,
                                        DetectabilityState.REGISTERED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_deprecatedToPendingTraining_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.DEPRECATED,
                                        DetectabilityState.PENDING_TRAINING))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_deprecatedToSupported_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.DEPRECATED,
                                        DetectabilityState.SUPPORTED))
                .isInstanceOf(IllegalTransitionException.class);
    }

    @Test
    void validateTransition_deprecatedToDeprecated_illegal() {
        assertThatThrownBy(
                        () ->
                                domainService.validateTransition(
                                        DetectabilityState.DEPRECATED,
                                        DetectabilityState.DEPRECATED))
                .isInstanceOf(IllegalTransitionException.class);
    }
}
