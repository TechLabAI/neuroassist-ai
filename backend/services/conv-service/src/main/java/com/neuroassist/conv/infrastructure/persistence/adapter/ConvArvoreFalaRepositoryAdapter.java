package com.neuroassist.conv.infrastructure.persistence.adapter;

import com.neuroassist.conv.application.port.out.ConvArvoreFalaRepositoryPort;
import com.neuroassist.conv.domain.model.ConvArvoreFala;
import com.neuroassist.conv.infrastructure.persistence.repository.ConvArvoreFalaJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConvArvoreFalaRepositoryAdapter implements ConvArvoreFalaRepositoryPort {

    private final ConvArvoreFalaJpaRepository repository;

    @Override
    public ConvArvoreFala save(ConvArvoreFala entity) {
        // TODO[SCAFFOLD]: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<ConvArvoreFala> findById(UUID id) {
        // TODO[SCAFFOLD]: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<ConvArvoreFala> findByTenantId(String tenantId) {
        // TODO[SCAFFOLD]: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
