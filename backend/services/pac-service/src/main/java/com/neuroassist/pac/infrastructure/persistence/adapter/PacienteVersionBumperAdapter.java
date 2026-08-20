package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.PacienteVersionBumper;
import com.neuroassist.pac.infrastructure.persistence.repository.PacienteJpaRepository;
import com.neuroassist.pac.shared.error.CodedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * PAC-019 — Adapter implementing {@link PacienteVersionBumper} via a JPA JPQL bulk-update.
 *
 * <p>Hexagonal rules:
 *
 * <ul>
 *   <li>RULE-03: no {@code @Transactional} — the caller's transaction boundary applies.
 *   <li>RULE-04/05: no {@code api.*} or cross-layer dependency.
 * </ul>
 *
 * <p>The {@code @Modifying} UPDATE in {@link PacienteJpaRepository#bumpCatalogoVersao} issues a
 * single SQL {@code UPDATE ... SET catalogo_versao = catalogo_versao + 1} within the caller's
 * transaction. This is atomic at the DB level and guaranteed to be committed with the lifecycle
 * change.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PacienteVersionBumperAdapter implements PacienteVersionBumper {

    private final PacienteJpaRepository repository;

    @Override
    public int bumpAndGet(UUID pacienteId) {
        int updated = repository.bumpCatalogoVersao(pacienteId);
        if (updated == 0) {
            throw new CodedException(
                    "not_found.paciente",
                    "Paciente not found for catalogoVersao bump: " + pacienteId);
        }
        // Read back the new value after the update
        int newVersion =
                repository
                        .findCatalogoVersaoById(pacienteId)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "not_found.paciente",
                                                "Paciente not found after bump: " + pacienteId));
        log.debug("catalogoVersao bumped pacienteId={} newVersion={}", pacienteId, newVersion);
        return newVersion;
    }

    @Override
    public int getCatalogoVersao(UUID pacienteId) {
        return repository.findCatalogoVersaoById(pacienteId).orElse(0);
    }
}
