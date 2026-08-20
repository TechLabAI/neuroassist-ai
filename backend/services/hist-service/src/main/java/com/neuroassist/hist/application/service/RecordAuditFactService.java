package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase;
import com.neuroassist.hist.application.port.out.AuditLedgerPort;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.application.port.out.HashChainPort;
import com.neuroassist.hist.application.port.out.IdempotencyPort;
import com.neuroassist.hist.application.port.out.StreamHeadPort;
import com.neuroassist.hist.domain.model.ActorRef;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.FactType;
import com.neuroassist.hist.domain.model.IdempotencyEntry;
import com.neuroassist.hist.domain.model.OccurredAtInFutureException;
import com.neuroassist.hist.domain.model.StreamHead;
import com.neuroassist.hist.domain.model.SubjectRef;
import com.neuroassist.hist.domain.model.SubjectType;
import com.neuroassist.hist.domain.model.UnknownFactTypeException;
import com.neuroassist.hist.domain.service.LgpdPayloadGuard;
import com.neuroassist.hist.infrastructure.config.HistMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — audit fact ingestion (HIST-006, P2 real implementation).
 *
 * <p>8-step append flow (within a single {@code @Transactional} boundary):
 *
 * <ol>
 *   <li>Validate factType against the controlled vocabulary.
 *   <li>Validate payload: reject biometric keys (LGPD guard).
 *   <li>Validate occurredAt: reject if more than 5 minutes in the future.
 *   <li>Idempotency check — if factId already recorded, return original record (ADR-005).
 *   <li>Acquire pessimistic write lock on the stream tip (ADR-006).
 *   <li>Derive prevHash and next sequenceNo from the stream head (or genesis defaults).
 *   <li>Build the AuditRecord and compute its SHA-256 recordHash (ADR-006).
 *   <li>Persist: insert ledger record + record idempotency entry + upsert stream head.
 * </ol>
 *
 * <p>TenantContextHolder (P5): security pin is applied by the filter layer in P5. In P2 the
 * tenantId is carried in the Command by the controller.
 *
 * <p>{@code @Transactional} only on the public method (RULE-03).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordAuditFactService implements RecordAuditFactUseCase {

    private static final Duration MAX_FUTURE_DRIFT = Duration.ofMinutes(5);

    private final IdempotencyPort idempotencyPort;
    private final StreamHeadPort streamHeadPort;
    private final HashChainPort hashChainPort;
    private final AuditLedgerPort auditLedgerPort;
    private final AuditRecordRepositoryPort auditRecordRepositoryPort;
    private final LgpdPayloadGuard lgpdPayloadGuard;
    private final HistMetrics histMetrics;

    @Override
    @Transactional
    public AppendResult append(Command command) {
        Timer.Sample sample = Timer.start();
        try {
            return doAppend(command);
        } finally {
            sample.stop(histMetrics.ingestionLatencyTimer(command.tenantId()));
        }
    }

    private AppendResult doAppend(Command command) {
        // Step 1: validate factType
        validateFactType(command.factType());

        // Step 2: validate payload (LGPD guard — delegated to LgpdPayloadGuard domain service)
        lgpdPayloadGuard.validate(command.payload());

        // Step 3: validate occurredAt clock-drift
        validateOccurredAt(command.occurredAt());

        // Step 4: idempotency check — same (tenantId, factId) → return original record
        Optional<IdempotencyEntry> existing =
                idempotencyPort.findByFactId(command.factId(), command.tenantId());
        if (existing.isPresent()) {
            UUID originalRecordId = existing.get().auditRecordId();
            AuditRecord original =
                    auditRecordRepositoryPort
                            .findById(originalRecordId, command.tenantId())
                            .orElseThrow(
                                    () ->
                                            new IllegalStateException(
                                                    "Idempotency entry references missing record id="
                                                            + originalRecordId));
            log.info(
                    "Idempotent replay: factId={} tenant={} originalRecordId={}",
                    command.factId(),
                    command.tenantId(),
                    originalRecordId);
            histMetrics.incrementDuplicateFactIgnored(command.tenantId(), command.factType());
            return new AppendResult(original, false);
        }

        // Step 5: acquire pessimistic write lock on stream tip
        SubjectRef subject = resolveSubject(command.subject());
        String streamKey = subject.streamKey();
        Optional<StreamHead> tipOpt = streamHeadPort.lockAndFetchTip(streamKey, command.tenantId());

        // Step 6: derive prevHash and sequenceNo from the stream head (or genesis defaults)
        String prevHash = tipOpt.map(StreamHead::currentHash).orElse(hashChainPort.genesis());
        long sequenceNo = tipOpt.map(head -> head.currentSequenceNo() + 1L).orElse(1L);

        // Step 7: build record (without recordHash first) and compute hash
        Instant recordedAt = Instant.now();
        ActorRef actor =
                new ActorRef(command.actor().caregiverId(), command.actor().displayNameSnapshot());

        // Build a pre-hash record to compute the canonical form
        UUID recordId = UUID.randomUUID();
        AuditRecord preHashRecord =
                new AuditRecord(
                        recordId,
                        command.tenantId(),
                        command.factId(),
                        command.factType(),
                        subject,
                        actor,
                        command.payload(),
                        command.occurredAt(),
                        recordedAt,
                        streamKey,
                        sequenceNo,
                        prevHash,
                        null, // recordHash placeholder — computed next
                        command.correctsFactId());

        String recordHash = hashChainPort.compute(prevHash, preHashRecord);

        // Build final immutable record with the computed hash
        AuditRecord record =
                new AuditRecord(
                        recordId,
                        command.tenantId(),
                        command.factId(),
                        command.factType(),
                        subject,
                        actor,
                        command.payload(),
                        command.occurredAt(),
                        recordedAt,
                        streamKey,
                        sequenceNo,
                        prevHash,
                        recordHash,
                        command.correctsFactId());

        // Step 8: persist — all three writes in the same transaction
        AuditRecord persisted = auditLedgerPort.insert(record, command.tenantId());

        idempotencyPort.record(
                new IdempotencyEntry(
                        command.factId(), command.tenantId(), persisted.id(), recordedAt),
                command.tenantId());

        streamHeadPort.upsert(
                new StreamHead(streamKey, command.tenantId(), sequenceNo, recordHash, recordedAt),
                command.tenantId());

        log.info(
                "Audit fact appended: factType={} streamKey={} sequenceNo={} tenant={}",
                command.factType(),
                streamKey,
                sequenceNo,
                command.tenantId());
        histMetrics.incrementAuditFactRecorded(command.tenantId(), command.factType());

        return new AppendResult(persisted, true);
    }

    // ---- private helpers ----

    private void validateFactType(String factType) {
        if (!FactType.isKnown(factType)) {
            throw new UnknownFactTypeException(factType);
        }
    }

    private void validateOccurredAt(Instant occurredAt) {
        if (occurredAt != null && occurredAt.isAfter(Instant.now().plus(MAX_FUTURE_DRIFT))) {
            throw new OccurredAtInFutureException(occurredAt);
        }
    }

    private SubjectRef resolveSubject(RecordAuditFactUseCase.SubjectCommand subjectCmd) {
        SubjectType type;
        try {
            type = SubjectType.valueOf(subjectCmd.type());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown subject type '"
                            + subjectCmd.type()
                            + "'. Must be one of: "
                            + Arrays.toString(SubjectType.values()));
        }
        return new SubjectRef(type, subjectCmd.id());
    }
}
