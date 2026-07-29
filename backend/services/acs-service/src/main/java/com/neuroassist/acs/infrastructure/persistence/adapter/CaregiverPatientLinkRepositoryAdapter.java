package com.neuroassist.acs.infrastructure.persistence.adapter;

import com.neuroassist.acs.application.port.out.CaregiverPatientLinkRepositoryPort;
import com.neuroassist.acs.domain.model.CaregiverPatientLink;
import com.neuroassist.acs.infrastructure.persistence.repository.CaregiverPatientLinkJpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaregiverPatientLinkRepositoryAdapter implements CaregiverPatientLinkRepositoryPort {

    private final CaregiverPatientLinkJpaRepository repository;

    @Override
    public CaregiverPatientLink save(CaregiverPatientLink entity) {
        // TODO: map domain model → JPA entity → save → map back
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Optional<CaregiverPatientLink> findById(UUID id) {
        // TODO: map JPA entity → domain model
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public List<CaregiverPatientLink> findByTenantId(String tenantId) {
        // TODO: map JPA entities → domain models
        throw new UnsupportedOperationException("not implemented");
    }
}
