package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.SigningKeyRepositoryPort;
import com.neuroassist.acs.domain.model.SigningKey;
import com.neuroassist.acs.infrastructure.persistence.repository.SigningKeyJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SigningKeyRepositoryAdapter implements SigningKeyRepositoryPort {

    private final SigningKeyJpaRepository repository;

    @Override
    public SigningKey save(SigningKey entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<SigningKey> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<SigningKey> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
