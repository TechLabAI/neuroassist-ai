package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.ComandoRepositoryPort;
import com.neuroassist.pac.domain.model.Comando;
import com.neuroassist.pac.infrastructure.persistence.repository.ComandoJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ComandoRepositoryAdapter implements ComandoRepositoryPort {

    private final ComandoJpaRepository repository;

    @Override
    public Comando save(Comando entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<Comando> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<Comando> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
