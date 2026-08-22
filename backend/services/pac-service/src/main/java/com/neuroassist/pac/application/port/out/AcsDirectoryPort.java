package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.CuidadorRef;
import com.neuroassist.pac.domain.model.EmpresaRef;
import java.util.UUID;

/**
 * Outbound port — ACS directory integration (INT-PAC-002 / SPEC-10 §5).
 *
 * <p>Used to resolve Empresa and Cuidador references when creating or updating a Paciente.
 * Implementations live in {@code infrastructure.clients} (RULE-06).
 *
 * <p>Failure semantics: 404 from ACS → throw CodedException("pac.cuidador.not-found", 422); ACS
 * unreachable → throw CodedException("acs.unavailable", 503).
 */
public interface AcsDirectoryPort {

    /**
     * Resolves an Empresa by id against the ACS service.
     *
     * @throws com.neuroassist.pac.shared.error.CodedException code="pac.cuidador.not-found" (422)
     *     if the empresa is unknown.
     * @throws com.neuroassist.pac.shared.error.CodedException code="acs.unavailable" (503) if ACS
     *     is unreachable.
     */
    EmpresaRef resolveTenant(UUID empresaId);

    /**
     * Resolves a Cuidador (caregiver) by id against the ACS service.
     *
     * @throws com.neuroassist.pac.shared.error.CodedException code="pac.cuidador.not-found" (422)
     *     if the cuidador is unknown.
     * @throws com.neuroassist.pac.shared.error.CodedException code="acs.unavailable" (503) if ACS
     *     is unreachable.
     */
    CuidadorRef resolveCuidador(UUID cuidadorId);
}
