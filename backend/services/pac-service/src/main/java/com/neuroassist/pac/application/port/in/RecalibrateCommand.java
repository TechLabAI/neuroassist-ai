package com.neuroassist.pac.application.port.in;

import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;

/**
 * Command object for the {@link ComandoLifecycleUseCase#recalibrate} use case (PAC-018).
 *
 * <p>Carries the new parameter values for in-place recalibration of a published Comando. All fields
 * are optional — {@code null} means "keep the existing value".
 *
 * <p>Gate re-run logic:
 *
 * <ul>
 *   <li>If {@code urgencia} escalates from {@code rotina} to {@code emergencia}, the emergency
 *       eligibility gate is re-executed (fail-closed).
 *   <li>If urgencia stays the same or lowers, the gate is NOT re-executed.
 * </ul>
 *
 * <p>RULE-01: no Spring imports. RULE-02: no JPA imports. Pure POJO record.
 */
public record RecalibrateCommand(
        Urgencia urgencia,
        Sensibilidade sensibilidade,
        Integer tempoMinGestoMs,
        Integer tempoMaxGestoMs) {}
