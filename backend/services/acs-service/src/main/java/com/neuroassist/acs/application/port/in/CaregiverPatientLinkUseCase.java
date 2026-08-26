package com.neuroassist.acs.application.port.in;

import com.neuroassist.acs.api.dto.CaregiverPatientLinkResponse;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port — CaregiverPatientLinkUseCase. Use cases for caregiver-patient link management.
 * Implemented by CaregiverPatientLinkService (application layer).
 */
public interface CaregiverPatientLinkUseCase {

    /**
     * Creates an active link between a caregiver and a patient. Verifies that caregiver exists and
     * belongs to the requested tenant. Throws CaregiverNotFoundException (404) if caregiver not
     * found. Throws TenantMismatchException (403) if caregiver belongs to different tenant.
     *
     * @param cuidadorId UUID of the caregiver
     * @param pacienteId UUID of the patient (logical cross-service reference)
     * @param tenantId UUID of the tenant context
     */
    CaregiverPatientLinkResponse createLink(UUID cuidadorId, UUID pacienteId, UUID tenantId);

    /**
     * Revokes the active link between a caregiver and a patient. SAFETY-CRITICAL (D-204): throws
     * LastCaregiverLinkException (409) if this would leave the patient without any active
     * caregiver.
     *
     * @param cuidadorId UUID of the caregiver
     * @param pacienteId UUID of the patient
     */
    CaregiverPatientLinkResponse revokeLink(UUID cuidadorId, UUID pacienteId);

    /**
     * Finds the active link between a caregiver and a patient, if it exists. Used by the controller
     * to decide HTTP 200 (idempotent) vs 201 (new link created).
     *
     * @param cuidadorId UUID of the caregiver
     * @param pacienteId UUID of the patient
     * @return Optional with the active link response, or empty if no active link exists
     */
    Optional<CaregiverPatientLinkResponse> findActiveLink(UUID cuidadorId, UUID pacienteId);

    /**
     * Revoga <b>todos</b> os vinculos ativos de um paciente cujos dados pessoais foram apagados
     * (LGPD).
     *
     * <p><b>Esta e a unica operacao que atravessa a invariante D-204, e por isso e uma operacao
     * separada.</b> A invariante existe para que o alarme de um paciente sempre tenha para onde ir;
     * um paciente apagado nao tem alarme a rotear. Manter o vinculo depois do apagamento nao
     * protege ninguem — apenas mantem viva a associacao "esta cuidadora cuida desta pessoa", que e
     * exatamente o tipo de vinculo que o apagamento se propos a remover.
     *
     * <p>Nao virou um parametro do {@code revokeLink} de proposito: burlar uma invariante marcada
     * SAFETY-CRITICAL nao pode ser um booleano que alguem passa por engano. E metodo proprio, com
     * authority propria, concedida so ao cliente de servico do PAC.
     *
     * <p>Idempotente: um paciente sem vinculos ativos devolve zero, sem erro.
     *
     * @return quantos vinculos foram revogados
     */
    int revokeAllForErasedPatient(UUID pacienteId);
}
