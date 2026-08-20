package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.StreamHeadPort;
import com.neuroassist.hist.domain.model.StreamHead;
import com.neuroassist.hist.infrastructure.persistence.entity.StreamHeadJpaEntity;
import com.neuroassist.hist.infrastructure.persistence.repository.StreamHeadJpaRepository;
import com.neuroassist.hist.infrastructure.security.TenantContextHolder;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter — manages the mutable stream-tip cache for hash-chain append serialisation
 * (ADR-006). {@link #lockAndFetchTip} acquires a PESSIMISTIC_WRITE lock so that concurrent appends
 * to the same stream are serialised within the same transaction boundary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StreamHeadAdapter implements StreamHeadPort {

    private final StreamHeadJpaRepository repository;
    private final EntityManager entityManager;

    @Override
    public Optional<StreamHead> lockAndFetchTip(String streamKey, String tenantId) {
        TenantContextHolder.require(); // belt-and-suspenders: tenant context must be active (P5)
        return repository
                .findByStreamKeyAndTenantIdForUpdate(streamKey, tenantId)
                .map(StreamHeadAdapter::toDomain);
    }

    @Override
    public StreamHead upsert(StreamHead head, String tenantId) {
        TenantContextHolder.require(); // belt-and-suspenders: tenant context must be active (P5)
        Optional<StreamHeadJpaEntity> existing =
                repository.findByStreamKeyAndTenantId(head.streamKey(), tenantId);

        if (existing.isPresent()) {
            StreamHeadJpaEntity entity = existing.get();
            entity.setCurrentSequenceNo(head.currentSequenceNo());
            entity.setCurrentHash(head.currentHash());
            entity.setLastRecordedAt(head.lastRecordedAt());
            repository.save(entity);
            log.debug(
                    "StreamHead updated: streamKey={} seq={} tenant={}",
                    head.streamKey(),
                    head.currentSequenceNo(),
                    tenantId);
        } else {
            StreamHeadJpaEntity entity = toEntity(head);
            entityManager.persist(entity);
            log.debug(
                    "StreamHead created (genesis): streamKey={} seq={} tenant={}",
                    head.streamKey(),
                    head.currentSequenceNo(),
                    tenantId);
        }
        return head;
    }

    // ---- mappers ----

    private static StreamHead toDomain(StreamHeadJpaEntity e) {
        return new StreamHead(
                e.getStreamKey(),
                e.getTenantId(),
                e.getCurrentSequenceNo(),
                e.getCurrentHash(),
                e.getLastRecordedAt());
    }

    private static StreamHeadJpaEntity toEntity(StreamHead h) {
        return StreamHeadJpaEntity.builder()
                .streamKey(h.streamKey())
                .tenantId(h.tenantId())
                .currentSequenceNo(h.currentSequenceNo())
                .currentHash(h.currentHash())
                .lastRecordedAt(h.lastRecordedAt())
                .build();
    }
}
