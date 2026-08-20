package com.neuroassist.hist.infrastructure.persistence.repository;

import com.neuroassist.hist.infrastructure.persistence.entity.StreamHeadJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StreamHeadJpaRepository extends JpaRepository<StreamHeadJpaEntity, UUID> {

    List<StreamHeadJpaEntity> findByTenantId(String tenantId);

    /**
     * Fetches the stream head with a PESSIMISTIC_WRITE lock to serialise concurrent appends
     * (ADR-006). Called by {@link
     * com.neuroassist.hist.infrastructure.persistence.adapter.StreamHeadAdapter#lockAndFetchTip}.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "SELECT s FROM StreamHeadJpaEntity s WHERE s.streamKey = :streamKey AND s.tenantId = :tenantId")
    Optional<StreamHeadJpaEntity> findByStreamKeyAndTenantIdForUpdate(
            @Param("streamKey") String streamKey, @Param("tenantId") String tenantId);

    /**
     * Plain lookup (no lock) used by the upsert path in {@link
     * com.neuroassist.hist.infrastructure.persistence.adapter.StreamHeadAdapter#upsert}.
     */
    Optional<StreamHeadJpaEntity> findByStreamKeyAndTenantId(String streamKey, String tenantId);
}
