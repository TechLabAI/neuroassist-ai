package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEnrollmentJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoEnrollmentJpaRepository
        extends JpaRepository<DispositivoEnrollmentJpaEntity, UUID> {

    List<DispositivoEnrollmentJpaEntity> findByTenantId(String tenantId);

    /**
     * Look up an enrollment record by its SHA-256 token hash. Hash is globally unique per the
     * unique constraint {@code ux_dispositivo_enrollment_token} on {@code token_hash}.
     */
    Optional<DispositivoEnrollmentJpaEntity> findByTokenHash(String tokenHash);
}
