package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.PacienteRepositoryPort;
import com.neuroassist.pac.domain.model.Paciente;
import com.neuroassist.pac.infrastructure.persistence.entity.PacienteJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.repository.PacienteJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * PAC-007 — Adapter implementing PacienteRepositoryPort via Spring Data JPA.
 *
 * <p>Hexagonal rule: no @Transactional here (RULE-03) — transaction boundary is in PacienteService.
 * No Spring in domain model (RULE-01/02). Maps between domain records and JPA entities.
 */
@Component
@RequiredArgsConstructor
public class PacienteRepositoryAdapter implements PacienteRepositoryPort {

    private final PacienteJpaRepository repository;

    @Override
    public Paciente save(Paciente paciente) {
        PacienteJpaEntity entity = toEntity(paciente);
        PacienteJpaEntity saved = repository.save(entity);
        return toDomain(saved);
    }

    @Override
    public Optional<Paciente> findByIdAndTenantId(UUID id, String tenantId) {
        return repository.findByIdAndTenantId(id, tenantId).map(this::toDomain);
    }

    @Override
    public List<Paciente> findByTenantId(String tenantId) {
        return repository.findByTenantId(tenantId).stream().map(this::toDomain).toList();
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private PacienteJpaEntity toEntity(Paciente p) {
        return PacienteJpaEntity.builder()
                .id(p.id())
                .tenantId(p.tenantId())
                .nome(p.nome())
                .dispositivoEdgeId(p.dispositivoEdgeId())
                .rowVersion(p.rowVersion())
                .catalogoVersao(p.catalogoVersao())
                .createdAt(p.createdAt())
                .updatedAt(p.updatedAt())
                .erasedAt(p.erasedAt())
                .build();
    }

    private Paciente toDomain(PacienteJpaEntity e) {
        // cuidadores are loaded separately via PacienteCuidadorRepositoryPort when needed
        return new Paciente(
                e.getId(),
                e.getTenantId(),
                e.getNome(),
                e.getDispositivoEdgeId(),
                List.of(),
                e.getRowVersion(),
                e.getCatalogoVersao(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getErasedAt());
    }
}
