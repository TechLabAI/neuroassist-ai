package com.neuroassist.pac.application.port.in;

import com.neuroassist.pac.domain.model.Comando;
import java.util.List;
import java.util.UUID;

/**
 * Inbound port — CatalogoAtivoUseCase (PAC-020).
 *
 * <p>Returns the active catalog for a patient: all {@code published} Comandos plus the current
 * {@code catalogoVersao} which serves as the HTTP ETag for conditional GET (304 Not Modified).
 *
 * <p>Consumed by GEST via {@code GET .../catalogo-ativo/{pacienteId}} to pull the active
 * gesture-to-command mapping. The ETag enables GEST to detect catalog changes without
 * re-downloading the full payload.
 */
public interface CatalogoAtivoUseCase {

    /**
     * Returns all published Comandos for a patient plus the current catalog version (ETag seed).
     *
     * <p>RULE-07: TenantContextHolder is required — the service resolves the tenant internally.
     *
     * @param pacienteId patient whose active catalog is requested
     * @return the active catalog result
     * @throws com.neuroassist.pac.shared.error.CodedException (not_found.paciente) if the patient
     *     does not exist in the current tenant schema
     */
    CatalogoAtivoResult getCatalogoAtivo(UUID pacienteId);

    /**
     * Result record for {@link #getCatalogoAtivo(UUID)}.
     *
     * @param comandos list of published Comandos (may be empty if none published yet)
     * @param catalogoVersao current catalog version — used as ETag value
     */
    record CatalogoAtivoResult(List<Comando> comandos, int catalogoVersao) {}
}
