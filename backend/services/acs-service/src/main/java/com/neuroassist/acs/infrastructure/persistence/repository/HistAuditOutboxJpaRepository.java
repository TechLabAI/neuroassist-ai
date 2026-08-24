package com.neuroassist.acs.infrastructure.persistence.repository;

import com.neuroassist.acs.infrastructure.persistence.entity.HistAuditOutboxJpaEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório Spring Data de {@link HistAuditOutboxJpaEntity} (E2E-031).
 *
 * <p>Mais antigas primeiro, limitado — apoiado no índice parcial {@code ix_acs_hist_outbox_pending
 * (created_at) WHERE status = 'pending'}.
 */
public interface HistAuditOutboxJpaRepository
        extends JpaRepository<HistAuditOutboxJpaEntity, UUID> {

    List<HistAuditOutboxJpaEntity> findByStatusOrderByCreatedAtAsc(
            String status, Pageable pageable);

    Optional<HistAuditOutboxJpaEntity> findByFactId(UUID factId);
}
