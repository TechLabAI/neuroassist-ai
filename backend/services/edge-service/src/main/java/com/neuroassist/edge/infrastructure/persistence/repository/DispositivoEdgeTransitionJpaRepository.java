package com.neuroassist.edge.infrastructure.persistence.repository;

import com.neuroassist.edge.domain.model.TransitionKind;
import com.neuroassist.edge.infrastructure.persistence.entity.DispositivoEdgeTransitionJpaEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DispositivoEdgeTransitionJpaRepository
        extends JpaRepository<DispositivoEdgeTransitionJpaEntity, UUID> {

    List<DispositivoEdgeTransitionJpaEntity> findByTenantId(String tenantId);

    /**
     * Ordered audit trail for a specific device. Caller controls result size via {@link Pageable}
     * (adapter uses {@code PageRequest.of(0, limit)}).
     */
    List<DispositivoEdgeTransitionJpaEntity> findByDispositivoEdgeIdOrderByAtDesc(
            UUID dispositivoEdgeId, Pageable pageable);

    /**
     * Latest transition of a given kind for a device — used by liveness to detect online↔offline
     * crossings.
     */
    Optional<DispositivoEdgeTransitionJpaEntity> findFirstByDispositivoEdgeIdAndKindOrderByAtDesc(
            UUID dispositivoEdgeId, TransitionKind kind);

    /**
     * Idempotency guard: returns true if a liveness transition to 'offline' already exists for this
     * device after {@code after}. Used by LivenessService to avoid duplicate online→offline rows
     * per staleness epoch.
     */
    boolean existsByDispositivoEdgeIdAndKindAndToStateAndAtAfter(
            UUID dispositivoEdgeId, TransitionKind kind, String toState, Instant after);
}
