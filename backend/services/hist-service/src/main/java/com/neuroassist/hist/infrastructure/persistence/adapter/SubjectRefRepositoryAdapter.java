package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.SubjectRefRepositoryPort;
import com.neuroassist.hist.domain.model.SubjectRef;
import com.neuroassist.hist.infrastructure.persistence.repository.SubjectRefJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SubjectRefRepositoryAdapter implements SubjectRefRepositoryPort {

    private final SubjectRefJpaRepository repository;

    @Override
    public SubjectRef save(SubjectRef entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<SubjectRef> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<SubjectRef> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
