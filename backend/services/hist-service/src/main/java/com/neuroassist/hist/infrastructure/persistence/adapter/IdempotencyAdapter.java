package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.IdempotencyPort;
import com.neuroassist.hist.domain.model.IdempotencyEntry;
import com.neuroassist.hist.infrastructure.persistence.entity.IdempotencyEntryJpaEntity;
import com.neuroassist.hist.infrastructure.persistence.repository.IdempotencyEntryJpaRepository;
import com.neuroassist.hist.infrastructure.security.TenantContextHolder;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter — implements the high-level {@link IdempotencyPort} domain port using
 * {@link IdempotencyEntryJpaRepository} (P3, HIST-010). One-to-one delegation; no business logic
 * here (kept in application.service per hexagonal rule).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdempotencyAdapter implements IdempotencyPort {

    private final IdempotencyEntryJpaRepository repository;

    @Override
    public Optional<IdempotencyEntry> findByFactId(UUID factId, String tenantId) {
        TenantContextHolder.require(); // belt-and-suspenders: tenant context must be active (P5)
        return repository
                .findByFactIdAndTenantId(factId, tenantId)
                .map(IdempotencyAdapter::toDomain);
    }

    @Override
    public void record(IdempotencyEntry entry, String tenantId) {
        TenantContextHolder.require(); // belt-and-suspenders: tenant context must be active (P5)
        IdempotencyEntryJpaEntity entity =
                IdempotencyEntryJpaEntity.builder()
                        .factId(entry.factId())
                        .tenantId(entry.tenantId())
                        .auditRecordId(entry.auditRecordId())
                        .recordedAt(entry.recordedAt())
                        .build();
        repository.save(entity);
        log.debug(
                "Idempotency entry recorded: factId={} tenant={} auditRecordId={}",
                entry.factId(),
                entry.tenantId(),
                entry.auditRecordId());
    }

    // ---- mapper ----

    private static IdempotencyEntry toDomain(IdempotencyEntryJpaEntity entity) {
        return new IdempotencyEntry(
                entity.getFactId(),
                entity.getTenantId(),
                entity.getAuditRecordId(),
                entity.getRecordedAt());
    }
}
