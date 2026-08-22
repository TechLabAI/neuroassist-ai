package com.neuroassist.edge.application.port.out;

import com.neuroassist.edge.domain.model.DispositivoEnrollment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DispositivoEnrollmentRepositoryPort {

    DispositivoEnrollment save(DispositivoEnrollment entity);

    Optional<DispositivoEnrollment> findById(UUID id);

    List<DispositivoEnrollment> findByTenantId(String tenantId);

    /**
     * Look up an enrollment record by its token hash (SHA-256). Hash is globally unique per the
     * unique constraint on token_hash.
     */
    Optional<DispositivoEnrollment> findByTokenHash(String tokenHash);
}
