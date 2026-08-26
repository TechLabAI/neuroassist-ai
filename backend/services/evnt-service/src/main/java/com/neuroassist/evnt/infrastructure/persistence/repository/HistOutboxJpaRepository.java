package com.neuroassist.evnt.infrastructure.persistence.repository;

import com.neuroassist.evnt.infrastructure.persistence.entity.HistOutboxJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data repository for {@link HistOutboxJpaEntity} (E2E-017).
 *
 * <p>Oldest pending first, bounded — backed by {@code ix_evnt_hist_outbox_pending (created_at)
 * WHERE status = 'pending'}.
 */
public interface HistOutboxJpaRepository extends JpaRepository<HistOutboxJpaEntity, UUID> {

    List<HistOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(String status, Pageable pageable);

    Optional<HistOutboxJpaEntity> findByFactId(UUID factId);
}
