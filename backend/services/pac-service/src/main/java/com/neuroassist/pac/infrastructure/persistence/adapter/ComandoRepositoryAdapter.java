package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.ComandoRepositoryPort;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.domain.model.EstadoComando;
import com.neuroassist.pac.domain.model.Sensibilidade;
import com.neuroassist.pac.domain.model.Urgencia;
import com.neuroassist.pac.infrastructure.persistence.entity.ComandoJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.repository.ComandoJpaRepository;
import com.neuroassist.pac.shared.error.CodedException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

/**
 * PAC-009 — Adapter implementing {@link ComandoRepositoryPort} via Spring Data JPA.
 *
 * <p>Hexagonal rules:
 *
 * <ul>
 *   <li>RULE-03: no {@code @Transactional} here — boundary is in application.service.
 *   <li>RULE-01/02: domain model Comando has no Spring/JPA imports.
 *   <li>RULE-04/05: no api.* or cross-layer dependency.
 * </ul>
 *
 * <p>Duplicate active mapping detection: the DB partial unique index {@code
 * ux_comando_active_mapping} (V0201) enforces the business rule at the DB level. This adapter
 * performs an advisory pre-flight check for a descriptive 422 response, and also catches {@code
 * DataIntegrityViolationException} as a safety net for concurrent inserts that pass the pre-flight
 * check simultaneously (TOCTOU).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ComandoRepositoryAdapter implements ComandoRepositoryPort {

    /**
     * Error code used for duplicate active mapping — maps to HTTP 422 in GlobalExceptionHandler.
     */
    private static final String CODE_DUPLICATE_ACTIVE_MAPPING =
            "pac.comando.duplicate-active-mapping";

    /**
     * Constraint name fragment used to identify the unique index violation. Must match the index
     * name defined in V0201__pac_comando.sql.
     */
    private static final String UX_ACTIVE_MAPPING = "ux_comando_active_mapping";

    private final ComandoJpaRepository repository;

    // -------------------------------------------------------------------------
    // Port implementation
    // -------------------------------------------------------------------------

    @Override
    public Comando save(Comando comando) {
        // Advisory pre-flight: surface a meaningful error before the constraint fires.
        // The DB index remains the authoritative guard.
        UUID excludeId =
                comando.id() != null
                        ? comando.id()
                        : UUID.fromString("00000000-0000-0000-0000-000000000000");
        if (repository.existsActiveMapping(
                comando.pacienteId(),
                comando.primitivoPrincipalId(),
                comando.primitivoSecundarioId(),
                comando.necessidade(),
                excludeId)) {
            throw new CodedException(
                    CODE_DUPLICATE_ACTIVE_MAPPING,
                    "An active mapping for the same gesture and need already exists for patient "
                            + comando.pacienteId());
        }

        try {
            ComandoJpaEntity entity = toEntity(comando);
            ComandoJpaEntity saved = repository.save(entity);
            return toDomain(saved);
        } catch (DataIntegrityViolationException ex) {
            // Safety net: concurrent insert that passed the advisory check above
            if (isActiveMappingViolation(ex)) {
                log.warn(
                        "Duplicate active mapping constraint violated for paciente={} principal={} necessidade={}",
                        comando.pacienteId(),
                        comando.primitivoPrincipalId(),
                        comando.necessidade());
                throw new CodedException(
                        CODE_DUPLICATE_ACTIVE_MAPPING,
                        "An active mapping for the same gesture and need already exists (concurrent insert)",
                        ex);
            }
            throw ex;
        }
    }

    @Override
    public Optional<Comando> findById(UUID id) {
        return repository.findById(id).map(this::toDomain);
    }

    @Override
    public Optional<Comando> findByIdAndPacienteId(UUID id, UUID pacienteId) {
        return repository.findByIdAndPacienteId(id, pacienteId).map(this::toDomain);
    }

    @Override
    public List<Comando> findByPacienteId(UUID pacienteId) {
        return repository.findByPacienteIdOrderByCreatedAtDesc(pacienteId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Comando> findActiveByPacienteId(UUID pacienteId) {
        return repository
                .findByPacienteIdAndEstadoNotOrderByCreatedAtDesc(
                        pacienteId, EstadoComando.retired.name())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<Comando> findPublishedByPacienteId(UUID pacienteId) {
        return repository
                .findByPacienteIdAndEstado(pacienteId, EstadoComando.published.name())
                .stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public boolean existsActiveMapping(
            UUID pacienteId,
            UUID primitivoPrincipalId,
            UUID primitivoSecundarioId,
            String necessidade,
            UUID excludeId) {
        UUID safeExclude =
                excludeId != null
                        ? excludeId
                        : UUID.fromString("00000000-0000-0000-0000-000000000000");
        return repository.existsActiveMapping(
                pacienteId, primitivoPrincipalId, primitivoSecundarioId, necessidade, safeExclude);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers (domain ↔ JPA entity)
    // -------------------------------------------------------------------------

    private ComandoJpaEntity toEntity(Comando c) {
        return ComandoJpaEntity.builder()
                .id(c.id())
                .pacienteId(c.pacienteId())
                .rotulo(c.rotulo())
                .necessidade(c.necessidade())
                .primitivoPrincipalId(c.primitivoPrincipalId())
                .primitivoSecundarioId(c.primitivoSecundarioId())
                .sensibilidade(c.sensibilidade() != null ? c.sensibilidade().name() : null)
                .tempoMinGestoMs(c.tempoMinGestoMs())
                .tempoMaxGestoMs(c.tempoMaxGestoMs())
                .urgencia(c.urgencia() != null ? c.urgencia().name() : null)
                .estado(c.estado() != null ? c.estado().name() : EstadoComando.drafted.name())
                .calibracaoVersao(c.calibracaoVersao())
                .rowVersion(c.rowVersion())
                .createdAt(c.createdAt() != null ? c.createdAt() : Instant.now())
                .updatedAt(c.updatedAt() != null ? c.updatedAt() : Instant.now())
                .build();
    }

    private Comando toDomain(ComandoJpaEntity e) {
        return new Comando(
                e.getId(),
                e.getPacienteId(),
                e.getRotulo(),
                e.getNecessidade(),
                e.getPrimitivoPrincipalId(),
                e.getPrimitivoSecundarioId(),
                e.getSensibilidade() != null ? Sensibilidade.valueOf(e.getSensibilidade()) : null,
                e.getTempoMinGestoMs(),
                e.getTempoMaxGestoMs(),
                e.getUrgencia() != null ? Urgencia.valueOf(e.getUrgencia()) : null,
                e.getEstado() != null ? EstadoComando.valueOf(e.getEstado()) : null,
                e.getCalibracaoVersao(),
                e.getRowVersion(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private boolean isActiveMappingViolation(DataIntegrityViolationException ex) {
        Throwable cause = ex.getCause();
        while (cause != null) {
            String msg = cause.getMessage();
            if (msg != null && msg.contains(UX_ACTIVE_MAPPING)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
