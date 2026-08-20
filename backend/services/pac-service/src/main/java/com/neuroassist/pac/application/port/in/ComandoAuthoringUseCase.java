package com.neuroassist.pac.application.port.in;

import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port — Comando authoring use cases (PAC-012, PAC-013 / SPEC-02 §5.1).
 *
 * <p>Accepts domain-primitive parameters; controllers are responsible for converting HTTP DTOs. No
 * Spring/JPA/HTTP imports allowed here (RULE-01/02 apply conceptually to all ports).
 */
public interface ComandoAuthoringUseCase {

    /**
     * Creates a new Comando draft scoped to the given Paciente (PAC-012).
     *
     * <p>Guards (SPEC-02 §5.1):
     *
     * <ol>
     *   <li>pacienteId belongs to the current tenant.
     *   <li>rotulo and necessidade non-blank (Bean Validation on DTO).
     *   <li>tempoMinGestoMs {@code > 0} and {@code <= tempoMaxGestoMs}.
     *   <li>If composite: primitivoSecundarioId {@code != primitivoPrincipalId}.
     *   <li>No active (non-retired) mapping with the same gesture+need for the patient.
     * </ol>
     *
     * @return persisted Comando with {@code estado = drafted}, {@code calibracaoVersao = 0}
     * @throws com.neuroassist.pac.shared.error.CodedException "not_found.paciente" (404) if
     *     pacienteId not found for current tenant
     * @throws com.neuroassist.pac.shared.error.CodedException "pac.comando.same-primitive" (400) if
     *     primitivoSecundarioId == primitivoPrincipalId
     * @throws com.neuroassist.pac.shared.error.CodedException "pac.timing.invalid" (400) if timing
     *     constraints are violated
     * @throws com.neuroassist.pac.shared.error.CodedException
     *     "pac.comando.duplicate-active-mapping" (422) if an active mapping for the same
     *     gesture+need already exists
     */
    Comando createDraft(
            UUID pacienteId,
            String rotulo,
            String necessidade,
            UUID primitivoPrincipalId,
            UUID primitivoSecundarioId,
            Sensibilidade sensibilidade,
            int tempoMinGestoMs,
            int tempoMaxGestoMs,
            Urgencia urgencia);

    /**
     * Edits an existing drafted Comando (PAC-013).
     *
     * <p>Guards:
     *
     * <ol>
     *   <li>pacienteId belongs to the current tenant.
     *   <li>Comando must be in {@code drafted} state — any other state → 409.
     *   <li>expectedRowVersion must match current rowVersion (optimistic lock).
     *   <li>tempoMinGestoMs {@code > 0} and {@code <= tempoMaxGestoMs}.
     *   <li>If composite: primitivoSecundarioId {@code != primitivoPrincipalId}.
     *   <li>No active duplicate mapping (advisory check + DB index).
     * </ol>
     *
     * @param expectedRowVersion rowVersion parsed from the {@code If-Match} request header
     * @return updated Comando with rowVersion bumped by 1
     * @throws com.neuroassist.pac.shared.error.CodedException
     *     "pac.comando.invalid-lifecycle-transition" (409) if Comando is not in {@code drafted}
     *     state
     * @throws com.neuroassist.pac.shared.error.CodedException "conflict-precondition" (409) if ETag
     *     is stale
     */
    Comando editDraft(
            UUID pacienteId,
            UUID comandoId,
            int expectedRowVersion,
            String rotulo,
            String necessidade,
            UUID primitivoPrincipalId,
            UUID primitivoSecundarioId,
            Sensibilidade sensibilidade,
            int tempoMinGestoMs,
            int tempoMaxGestoMs,
            Urgencia urgencia);

    /**
     * Returns all Comandos for the given Paciente, ordered newest-first.
     *
     * @throws com.neuroassist.pac.shared.error.CodedException "not_found.paciente" (404) if
     *     pacienteId not found for current tenant
     */
    List<Comando> listByPaciente(UUID pacienteId);

    /**
     * Returns a single Comando scoped to the given Paciente.
     *
     * @throws com.neuroassist.pac.shared.error.CodedException "not_found.comando" (404) if comando
     *     not found for the given patient
     */
    Comando findByIdAndPacienteId(UUID pacienteId, UUID comandoId);
}
