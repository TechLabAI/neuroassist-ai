package com.neuroassist.hist.infrastructure.persistence.adapter;

import com.neuroassist.hist.application.port.out.AuditLedgerPort;
import com.neuroassist.hist.domain.model.ActorRef;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.SubjectRef;
import com.neuroassist.hist.domain.model.SubjectType;
import com.neuroassist.hist.infrastructure.persistence.entity.AuditRecordJpaEntity;
import com.neuroassist.hist.infrastructure.security.TenantContextHolder;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter — write-once insert of an {@link AuditRecord} into the tenant ledger
 * (ADR-004). Uses {@link EntityManager#persist} exclusively — never {@code save()} or {@code
 * merge()} — to enforce the write-once invariant at the persistence boundary.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLedgerAdapter implements AuditLedgerPort {

    private final EntityManager entityManager;

    @Override
    public AuditRecord insert(AuditRecord record, String tenantId) {
        TenantContextHolder.require(); // belt-and-suspenders: tenant context must be active (P5)
        AuditRecordJpaEntity entity = toEntity(record);
        entityManager.persist(entity);
        log.debug(
                "AuditRecord persisted: id={} streamKey={} seq={} tenant={}",
                record.id(),
                record.streamKey(),
                record.sequenceNo(),
                tenantId);
        return record; // caller already holds the complete domain record — return as-is
    }

    // ---- mapper (domain → JPA entity) ----

    private static AuditRecordJpaEntity toEntity(AuditRecord r) {
        SubjectRef subject = r.subject();
        ActorRef actor = r.actor();

        return AuditRecordJpaEntity.builder()
                .id(r.id())
                .tenantId(r.tenantId())
                .factId(r.factId())
                .factType(r.factType())
                .subjectType(subject != null ? subject.type().name() : null)
                .subjectId(subject != null ? subject.id() : null)
                .caregiverId(actor != null ? actor.caregiverId() : null)
                .actorDisplayName(actor != null ? actor.displayNameSnapshot() : null)
                .payload(r.payload())
                // Truncate to microseconds before persisting: PostgreSQL TIMESTAMPTZ has
                // microsecond precision and ROUNDS sub-microsecond nanoseconds, which would
                // produce a different value than the truncation used in SHA-256 canonical form
                // (Sha256HashChainAdapter.canonicalize), causing spurious hash mismatches during
                // chain verification (ADR-006, Windows JVM 100-ns Instant precision).
                .occurredAt(truncateMicros(r.occurredAt()))
                .recordedAt(truncateMicros(r.recordedAt()))
                .streamKey(r.streamKey())
                .sequenceNo(r.sequenceNo())
                .prevHash(r.prevHash())
                .recordHash(r.recordHash())
                .correctsRecordId(r.correctsRecordId())
                .build();
    }

    private static Instant truncateMicros(Instant instant) {
        return instant != null ? instant.truncatedTo(ChronoUnit.MICROS) : null;
    }

    /** Converts a JPA entity back to the domain record (used by read paths in other adapters). */
    public static AuditRecord toDomain(AuditRecordJpaEntity e) {
        SubjectRef subject =
                e.getSubjectType() != null && e.getSubjectId() != null
                        ? new SubjectRef(SubjectType.valueOf(e.getSubjectType()), e.getSubjectId())
                        : null;
        ActorRef actor =
                e.getCaregiverId() != null
                        ? new ActorRef(e.getCaregiverId(), e.getActorDisplayName())
                        : null;
        return new AuditRecord(
                e.getId(),
                e.getTenantId(),
                e.getFactId(),
                e.getFactType(),
                subject,
                actor,
                e.getPayload(),
                e.getOccurredAt(),
                e.getRecordedAt(),
                e.getStreamKey(),
                e.getSequenceNo(),
                e.getPrevHash(),
                e.getRecordHash(),
                e.getCorrectsRecordId());
    }
}
