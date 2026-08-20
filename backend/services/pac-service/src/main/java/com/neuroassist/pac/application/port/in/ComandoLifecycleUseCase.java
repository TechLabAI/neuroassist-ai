package com.neuroassist.pac.application.port.in;

import com.neuroassist.pac.domain.model.Comando;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port — Comando lifecycle use cases (PAC-014, PAC-015, PAC-016, PAC-018).
 *
 * <p>State machine: {@code drafted → validated → published → (recalibrated ↻) → retired}.
 * Recalibrate ({@code published ↻ published}) is implemented in P6 (PAC-018).
 *
 * <p>All operations are tenant-scoped: callers do not pass tenantId — the service reads it from
 * {@code TenantContextHolder} (RULE-07).
 */
public interface ComandoLifecycleUseCase {

    /**
     * PAC-014 — Transition {@code drafted → validated}.
     *
     * <p>Guards: estado must be {@code drafted}; all other states → 409.
     *
     * @param pacienteId patient owning the command (cross-patient access guard)
     * @param comandoId command to validate
     * @return the updated Comando domain object in {@code validated} state
     * @throws com.neuroassist.pac.shared.error.CodedException
     *     pac.comando.invalid-lifecycle-transition (409) if the command is not in {@code drafted}
     *     state
     */
    Comando validate(UUID pacienteId, UUID comandoId);

    /**
     * PAC-015 — Transition {@code validated → published} with emergency eligibility gate.
     *
     * <p>Guard: estado must be {@code validated}; all other states → 409. Emergency gate (SPEC-03):
     * for urgencia=emergencia the gate is fail-closed.
     *
     * @param pacienteId patient owning the command
     * @param comandoId command to publish
     * @return {@link PublishResult} containing the updated Comando and any advisory warnings
     * @throws com.neuroassist.pac.shared.error.EligibilityGateUnavailableException (503) for
     *     emergency urgency when GEST is unreachable or CB is OPEN
     * @throws com.neuroassist.pac.shared.error.CodedException pac.emergency.not-detectable (422)
     *     for emergency urgency when GEST reports supported=false
     * @throws com.neuroassist.pac.shared.error.CodedException
     *     pac.comando.invalid-lifecycle-transition (409) if estado is not {@code validated}
     */
    PublishResult publish(UUID pacienteId, UUID comandoId);

    /**
     * PAC-016 — Transition any non-retired state → {@code retired} (soft delete, ADR-006).
     *
     * <p>Guard: already {@code retired} → 409. The row is NOT hard-deleted (ADR-006).
     *
     * @param pacienteId patient owning the command
     * @param comandoId command to retire
     * @return the updated Comando domain object in {@code retired} state
     * @throws com.neuroassist.pac.shared.error.CodedException
     *     pac.comando.invalid-lifecycle-transition (409) if the command is already {@code retired}
     */
    Comando retire(UUID pacienteId, UUID comandoId);

    /**
     * PAC-018 — In-place recalibration of a {@code published} Comando (P6).
     *
     * <p>The Comando stays in {@code published} state; {@code calibracaoVersao} is bumped by 1. The
     * patient's {@code catalogoVersao} is also incremented atomically.
     *
     * <p>Gate re-run rule (SPEC-02 §5.4):
     *
     * <ul>
     *   <li>Urgency <b>escalation</b> ({@code rotina → emergencia}): gate IS re-executed
     *       (fail-closed — 503 if unavailable, 422 if not detectable).
     *   <li>Urgency <b>same or lower</b> ({@code emergencia → rotina} or no change): gate is NOT
     *       re-executed.
     * </ul>
     *
     * <p>A {@code comando.recalibrated} domain fact is recorded to HIST via outbox (resilient —
     * HIST unavailability never blocks the lifecycle commit).
     *
     * @param pacienteId patient owning the command
     * @param comandoId command to recalibrate (must be in {@code published} state)
     * @param command new parameter values; {@code null} fields keep the existing value
     * @return the updated Comando with {@code calibracaoVersao} +1
     * @throws com.neuroassist.pac.shared.error.CodedException
     *     pac.comando.invalid-lifecycle-transition (409) if estado is not {@code published}
     * @throws com.neuroassist.pac.shared.error.EligibilityGateUnavailableException (503) when
     *     urgency is escalated and GEST is unreachable
     * @throws com.neuroassist.pac.shared.error.CodedException pac.emergency.not-detectable (422)
     *     when urgency is escalated and GEST reports not detectable
     */
    Comando recalibrate(UUID pacienteId, UUID comandoId, RecalibrateCommand command);

    /**
     * Result record for {@link #publish(UUID, UUID)}.
     *
     * @param comando the updated Comando in {@code published} state
     * @param warnings advisory messages (e.g. GEST unavailable for routine urgency); empty list for
     *     clean publish
     */
    record PublishResult(Comando comando, List<String> warnings) {}
}
