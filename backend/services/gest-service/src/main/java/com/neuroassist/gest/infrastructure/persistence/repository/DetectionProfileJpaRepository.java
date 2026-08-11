package com.neuroassist.gest.infrastructure.persistence.repository;

import com.neuroassist.gest.infrastructure.persistence.entity.DetectionProfileJpaEntity;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DetectionProfileJpaRepository
        extends JpaRepository<DetectionProfileJpaEntity, UUID> {

    // Multi-tenancy is via schema routing (search_path), not a tenant_id column.

    Optional<DetectionProfileJpaEntity> findByActiveTrue();

    Optional<DetectionProfileJpaEntity> findByVersion(Long version);

    /**
     * Returns the next monotonic version number for this tenant's profile sequence. Uses {@code
     * COALESCE(MAX(version), 0) + 1} so the first version is 1. Executed with a pessimistic write
     * lock to prevent concurrent duplicate versions (GEST-012 — R4: PESSIMISTIC_WRITE accepted as
     * MVP trade-off).
     *
     * <p>Must be called within an active transaction (provided by the caller in {@code
     * application.service} — RULE-03 ensures @Transactional is only there).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT COALESCE(MAX(d.version), 0) + 1 FROM DetectionProfileJpaEntity d")
    long nextVersion();

    /**
     * Marks all currently-active profiles for this tenant as inactive. Called before persisting a
     * new active profile so only one profile is active at a time.
     *
     * <p>Must be called within an active transaction (provided by the caller — RULE-03).
     */
    @Modifying
    @Query("UPDATE DetectionProfileJpaEntity d SET d.active = false WHERE d.active = true")
    void deactivateCurrent();
}
