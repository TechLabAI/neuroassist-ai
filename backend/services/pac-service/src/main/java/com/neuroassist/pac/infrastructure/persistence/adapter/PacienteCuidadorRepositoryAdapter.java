package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.PacienteCuidadorRepositoryPort;
import com.neuroassist.pac.domain.model.PacienteCuidador;
import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorId;
import com.neuroassist.pac.infrastructure.persistence.entity.PacienteCuidadorJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.repository.PacienteCuidadorJpaRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Adapter implementing PacienteCuidadorRepositoryPort via Spring Data JPA.
 *
 * <p>No @Transactional here (RULE-03). Transaction boundary in PacienteService. The composite PK
 * (paciente_id, cuidador_responsavel_id) is modeled via @EmbeddedId.
 */
@Component
@RequiredArgsConstructor
public class PacienteCuidadorRepositoryAdapter implements PacienteCuidadorRepositoryPort {

    private final PacienteCuidadorJpaRepository repository;

    @Override
    public List<PacienteCuidador> saveAll(List<PacienteCuidador> cuidadores) {
        List<PacienteCuidadorJpaEntity> entities = cuidadores.stream().map(this::toEntity).toList();
        List<PacienteCuidadorJpaEntity> saved = repository.saveAll(entities);
        return saved.stream().map(this::toDomain).toList();
    }

    @Override
    public List<PacienteCuidador> findByPacienteId(UUID pacienteId) {
        return repository.findByIdPacienteId(pacienteId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteByPacienteId(UUID pacienteId) {
        repository.deleteByPacienteId(pacienteId);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private PacienteCuidadorJpaEntity toEntity(PacienteCuidador d) {
        PacienteCuidadorId embId =
                new PacienteCuidadorId(d.pacienteId(), d.cuidadorResponsavelId());
        return PacienteCuidadorJpaEntity.builder()
                .id(embId)
                .createdAt(d.createdAt() != null ? d.createdAt() : Instant.now())
                .build();
    }

    private PacienteCuidador toDomain(PacienteCuidadorJpaEntity e) {
        return new PacienteCuidador(
                e.getId().getPacienteId(), e.getId().getCuidadorResponsavelId(), e.getCreatedAt());
    }
}
