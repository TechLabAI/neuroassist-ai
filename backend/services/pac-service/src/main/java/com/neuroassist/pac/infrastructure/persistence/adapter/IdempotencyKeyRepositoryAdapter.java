package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.IdempotencyKeyRepositoryPort;
import com.neuroassist.pac.domain.model.IdempotencyKeyRecord;
import com.neuroassist.pac.infrastructure.persistence.entity.IdempotencyKeyJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.repository.IdempotencyKeyJpaRepository;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Persistence adapter implementing {@link IdempotencyKeyRepositoryPort} (PAC-023).
 *
 * <p>Architectural rules:
 *
 * <ul>
 *   <li>RULE-05: no {@code api.*} imports.
 *   <li>RULE-03: no {@code @Transactional} here — transaction boundary is in {@link
 *       com.neuroassist.pac.application.service.IdempotencyService}.
 *   <li>Never exposes JPA entity beyond this adapter — always maps to/from {@link
 *       IdempotencyKeyRecord}.
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class IdempotencyKeyRepositoryAdapter implements IdempotencyKeyRepositoryPort {

    private final IdempotencyKeyJpaRepository jpaRepository;

    @Override
    public Optional<IdempotencyKeyRecord> findByKey(String compositeKey) {
        return jpaRepository.findById(compositeKey).map(this::toDomain);
    }

    @Override
    public IdempotencyKeyRecord save(IdempotencyKeyRecord record) {
        IdempotencyKeyJpaEntity entity =
                IdempotencyKeyJpaEntity.builder()
                        .key(record.key())
                        .resourceId(record.resourceId())
                        .bodyHash(record.bodyHash())
                        .createdAt(record.createdAt())
                        .build();
        return toDomain(jpaRepository.save(entity));
    }

    @Override
    public void deleteExpiredBefore(Instant cutoff) {
        jpaRepository.deleteExpiredBefore(cutoff);
    }

    // -------------------------------------------------------------------------
    // Mapping helpers
    // -------------------------------------------------------------------------

    private IdempotencyKeyRecord toDomain(IdempotencyKeyJpaEntity e) {
        return new IdempotencyKeyRecord(
                e.getKey(), e.getResourceId(), e.getBodyHash(), e.getCreatedAt());
    }
}
