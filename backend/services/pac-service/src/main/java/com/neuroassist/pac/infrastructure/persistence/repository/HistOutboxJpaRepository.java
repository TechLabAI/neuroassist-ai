package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.HistOutboxJpaEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link HistOutboxJpaEntity}.
 *
 * <p>Used by {@link com.neuroassist.pac.infrastructure.clients.HistFactAdapter} to persist pending
 * domain facts and update their delivery status.
 */
public interface HistOutboxJpaRepository extends JpaRepository<HistOutboxJpaEntity, UUID> {

    /**
     * Returns all pending outbox entries ordered by creation time (oldest first). Used by retry
     * mechanisms (scheduled task, future P9+).
     */
    List<HistOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(String status);

    /**
     * Oldest pending entries first, bounded. Backed by the partial index {@code
     * ix_hist_outbox_pending (created_at) WHERE status = 'pending'}.
     */
    List<HistOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);
}
