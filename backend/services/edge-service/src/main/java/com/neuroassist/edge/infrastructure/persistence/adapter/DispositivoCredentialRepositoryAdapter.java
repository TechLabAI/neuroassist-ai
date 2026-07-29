package com.neuroassist.edge.infrastructure.persistence.adapter;

import com.neuroassist.edge.application.port.out.DispositivoCredentialRepositoryPort;
import com.neuroassist.edge.domain.model.DispositivoCredential;
import com.neuroassist.edge.infrastructure.persistence.repository.DispositivoCredentialJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DispositivoCredentialRepositoryAdapter implements DispositivoCredentialRepositoryPort {

    private final DispositivoCredentialJpaRepository repository;

    @Override
    public DispositivoCredential save(DispositivoCredential entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<DispositivoCredential> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<DispositivoCredential> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
