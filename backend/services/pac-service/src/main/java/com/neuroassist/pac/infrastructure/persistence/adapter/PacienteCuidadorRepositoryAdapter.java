package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.PacienteCuidadorRepositoryPort;
import com.neuroassist.pac.domain.model.PacienteCuidador;
import com.neuroassist.pac.infrastructure.persistence.repository.PacienteCuidadorJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PacienteCuidadorRepositoryAdapter implements PacienteCuidadorRepositoryPort {

    private final PacienteCuidadorJpaRepository repository;

    @Override
    public PacienteCuidador save(PacienteCuidador entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<PacienteCuidador> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<PacienteCuidador> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
