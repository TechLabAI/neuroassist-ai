package com.neuroassist.conv.infrastructure.persistence.adapter;

import com.neuroassist.conv.application.port.out.ConvNoArvoreFalaRepositoryPort;
import com.neuroassist.conv.domain.model.ConvNoArvoreFala;
import com.neuroassist.conv.infrastructure.persistence.repository.ConvNoArvoreFalaJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ConvNoArvoreFalaRepositoryAdapter implements ConvNoArvoreFalaRepositoryPort {

    private final ConvNoArvoreFalaJpaRepository repository;

    @Override
    public ConvNoArvoreFala save(ConvNoArvoreFala entity) {
        // TODO[SCAFFOLD]: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<ConvNoArvoreFala> findById(UUID id) {
        // TODO[SCAFFOLD]: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<ConvNoArvoreFala> findByTenantId(String tenantId) {
        // TODO[SCAFFOLD]: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
