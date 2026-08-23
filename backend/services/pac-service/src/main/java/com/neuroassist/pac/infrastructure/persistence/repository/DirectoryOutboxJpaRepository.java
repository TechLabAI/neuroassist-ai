package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.DirectoryOutboxJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link DirectoryOutboxJpaEntity} (E2E-015).
 *
 * <p>Oldest pending first, bounded — backed by the partial index {@code ix_directory_outbox_pending
 * (created_at) WHERE status = 'pending'}. The ordering is part of the contract, not an
 * optimisation: see {@code DirectoryOutboxPort#findPending}.
 */
public interface DirectoryOutboxJpaRepository
        extends JpaRepository<DirectoryOutboxJpaEntity, UUID> {

    List<DirectoryOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(
            String status, Pageable pageable);
}
