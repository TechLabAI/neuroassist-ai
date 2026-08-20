package com.neuroassist.pac.application.service;

import com.neuroassist.pac.application.port.in.CatalogoAtivoUseCase;
import com.neuroassist.pac.application.port.out.ComandoRepositoryPort;
import com.neuroassist.pac.application.port.out.PacienteRepositoryPort;
import com.neuroassist.pac.application.port.out.PacienteVersionBumper;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.infrastructure.security.TenantContextHolder;
import com.neuroassist.pac.shared.error.CodedException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PAC-020 — Active catalog read use case.
 *
 * <p>Returns all {@code published} Comandos for a patient plus the current {@code catalogoVersao}
 * to support conditional GET with ETag/304 Not Modified.
 *
 * <p>Hexagonal rules enforced:
 *
 * <ul>
 *   <li>RULE-03: {@code @Transactional} only here.
 *   <li>RULE-07: {@code TenantContextHolder.require()} first.
 *   <li>RULE-04/05: only port interfaces injected.
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogoAtivoService implements CatalogoAtivoUseCase {

    private final ComandoRepositoryPort comandoRepository;
    private final PacienteRepositoryPort pacienteRepository;
    private final PacienteVersionBumper pacienteVersionBumper;

    /**
     * Returns the active catalog for a patient (published commands + catalogoVersao).
     *
     * <p>Read-only transaction: no state changes occur. The {@code catalogoVersao} is read via
     * {@link PacienteVersionBumper#getCatalogoVersao} to avoid loading the full entity.
     */
    @Override
    @Transactional(readOnly = true)
    public CatalogoAtivoResult getCatalogoAtivo(UUID pacienteId) {
        String tenantId = TenantContextHolder.require(); // RULE-07

        // Assert patient exists in this tenant (defense-in-depth)
        pacienteRepository
                .findByIdAndTenantId(pacienteId, tenantId)
                .orElseThrow(
                        () ->
                                new CodedException(
                                        "not_found.paciente", "Paciente not found: " + pacienteId));

        List<Comando> published = comandoRepository.findPublishedByPacienteId(pacienteId);
        int catalogoVersao = pacienteVersionBumper.getCatalogoVersao(pacienteId);

        log.debug(
                "catalogo-ativo pacienteId={} tenant={} published={} catalogoVersao={}",
                pacienteId,
                tenantId,
                published.size(),
                catalogoVersao);

        return new CatalogoAtivoResult(published, catalogoVersao);
    }
}
