package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.HashChainRepositoryPort;
import com.neuroassist.hist.domain.model.HashChain;
import com.neuroassist.hist.infrastructure.persistence.repository.HashChainJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class HashChainRepositoryAdapter implements HashChainRepositoryPort {

    private final HashChainJpaRepository repository;

    @Override
    public HashChain save(HashChain entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<HashChain> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<HashChain> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
