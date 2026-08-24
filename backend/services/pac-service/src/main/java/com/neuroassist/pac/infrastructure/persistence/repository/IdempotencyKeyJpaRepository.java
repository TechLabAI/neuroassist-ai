package com.neuroassist.pac.infrastructure.persistence.repository;

import com.neuroassist.pac.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA repository for {@link IdempotencyKeyJpaEntity} (PAC-023).
 *
 * <p>PK type is {@code String} (composite key: {@code {resourceType}:{callerKey}}). The inherited
 * {@code findById} covers the lookup case; {@code save} covers registration. {@code
 * deleteExpiredBefore} supports lazy TTL cleanup (SPEC-05 §3.3).
 */
public interface IdempotencyKeyJpaRepository
        extends JpaRepository<IdempotencyKeyJpaEntity, String> {

    /**
     * Deletes all idempotency key rows whose {@code created_at} is before {@code cutoff}. Called
     * lazily by {@link
     * com.neuroassist.pac.infrastructure.persistence.adapter.IdempotencyKeyRepositoryAdapter}.
     *
     * @param cutoff delete rows strictly older than this instant (e.g. {@code now() - 24h})
     * @return number of deleted rows
     */
    // Consistencia com as outras consultas em massa deste servico: o contexto nao pode seguir
    // achando que as chaves apagadas existem.
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("DELETE FROM IdempotencyKeyJpaEntity e WHERE e.createdAt < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") Instant cutoff);
}
