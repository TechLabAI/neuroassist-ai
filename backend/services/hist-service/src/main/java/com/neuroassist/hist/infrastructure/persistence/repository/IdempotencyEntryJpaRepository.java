package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.IdempotencyEntryJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IdempotencyEntryJpaRepository
        extends JpaRepository<IdempotencyEntryJpaEntity, UUID> {

    List<IdempotencyEntryJpaEntity> findByTenantId(String tenantId);

    /**
     * Looks up an idempotency entry by the producer-minted factId scoped to the given tenant. Used
     * by {@link com.neuroassist.hist.infrastructure.persistence.adapter.IdempotencyAdapter}.
     */
    Optional<IdempotencyEntryJpaEntity> findByFactIdAndTenantId(UUID factId, String tenantId);
}
