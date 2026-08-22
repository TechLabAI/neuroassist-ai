package com.neuroassist.hist.application.port.out;

import com.neuroassist.hist.domain.model.SubjectRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRefRepositoryPort {

    SubjectRef save(SubjectRef entity);

    Optional<SubjectRef> findById(UUID id);

    List<SubjectRef> findByTenantId(String tenantId);

    // TODO: add domain-specific query methods
}
