package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.TipoGestoRefRepositoryPort;
import com.neuroassist.pac.domain.model.TipoGestoRef;
import com.neuroassist.pac.infrastructure.persistence.repository.TipoGestoRefJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TipoGestoRefRepositoryAdapter implements TipoGestoRefRepositoryPort {

    private final TipoGestoRefJpaRepository repository;

    @Override
    public TipoGestoRef save(TipoGestoRef entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<TipoGestoRef> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<TipoGestoRef> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
