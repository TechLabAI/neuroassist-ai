package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase;
import com.neuroassist.hist.application.port.out.AuditLedgerPort;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.application.port.out.HashChainPort;
import com.neuroassist.hist.application.port.out.IdempotencyPort;
import com.neuroassist.hist.application.port.out.StreamHeadPort;
import com.neuroassist.hist.domain.model.ActorRef;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.CorrectsTargetNotFoundException;
import com.neuroassist.hist.domain.model.IdempotencyEntry;
import com.neuroassist.hist.domain.model.InvalidFactTypeException;
import com.neuroassist.hist.domain.model.OccurredAtInFutureException;
import com.neuroassist.hist.domain.model.StreamHead;
import com.neuroassist.hist.domain.model.SubjectRef;
import com.neuroassist.hist.domain.model.SubjectType;
import com.neuroassist.hist.domain.service.LgpdPayloadGuard;
import com.neuroassist.hist.infrastructure.config.FactTypeCatalogProperties;
import com.neuroassist.hist.infrastructure.config.HistMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — audit fact ingestion (HIST-006, P2 real implementation).
 *
 * <p>8-step append flow (within a single {@code @Transactional} boundary):
 *
 * <ol>
 *   <li>Validate factType against the configured vocabulary ({@code hist.fact-types.allowed}).
 *   <li>Validate payload: reject biometric keys (LGPD guard).
 *   <li>Validate occurredAt: reject if more than 5 minutes in the future.
 *   <li>Idempotency check — if factId already recorded, return original record (ADR-005).
 *   <li>Acquire pessimistic write lock on the stream tip (ADR-006).
 *   <li>Derive prevHash and next sequenceNo from the stream head (or genesis defaults).
 *   <li>Build the AuditRecord and compute its SHA-256 recordHash (ADR-006).
 *   <li>Persist: insert ledger record + record idempotency entry + upsert stream head.
 * </ol>
 *
 * <p>Stream-tip contention retry (HIST-022 / SPEC-05): {@code @Retryable} wraps the entire
 * transactional append with {@code maxAttempts=3}, {@code backoff=50ms}. Only pessimistic-lock
 * failures (deadlocks / lock-wait timeouts) trigger a retry — domain validation exceptions ({@link
 * InvalidFactTypeException}, etc.) propagate immediately without retry.
 *
 * <p>FactType vocabulary (HIST-023 / SPEC-02): validated against {@code hist.fact-types.allowed} in
 * {@code application.yml} — no recompilation required to extend the vocabulary.
 *
 * <p>TenantContextHolder (P5): security pin applied by the filter layer. tenantId is carried in the
 * Command by the controller.
 *
 * <p>{@code @Transactional} only on the public method (RULE-03). {@code @Retryable} retry
 * interceptor is outer; transaction interceptor is inner (new TX per attempt, required for
 * pessimistic-lock replay semantics).
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
    private final FactTypeCatalogProperties catalogProperties;

    /**
     * Appends an audit fact to the ledger.
     *
     * <p>Stream-tip contention retry — {@code maxAttempts=3}, {@code backoff=50ms} (HIST-022). Only
     * {@link PessimisticLockingFailureException} (deadlock / lock timeout from {@code
     * StreamHeadAdapter.lockAndFetchTip}) triggers a retry; domain validation exceptions propagate
     * immediately.
     */
    @Override
    @Retryable(
            retryFor = {
                PessimisticLockingFailureException.class,
                DataIntegrityViolationException.class
            },
            maxAttemptsExpression = "${hist.retry.max-attempts:3}",
            backoff = @Backoff(delayExpression = "${hist.retry.backoff-ms:50}"))
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
        // Step 1: validate factType against the configured vocabulary (HIST-023)
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

        // Step 4b: validate correctsFactId and resolve to the HIST internal record UUID.
        // If correctsFactId is set but doesn't exist in this tenant → 422 (ADR-005).
        UUID resolvedCorrectsRecordId = null;
        if (command.correctsFactId() != null) {
            Optional<IdempotencyEntry> correctsEntry =
                    idempotencyPort.findByFactId(command.correctsFactId(), command.tenantId());
            if (correctsEntry.isEmpty()) {
                throw new CorrectsTargetNotFoundException(command.correctsFactId());
            }
            resolvedCorrectsRecordId = correctsEntry.get().auditRecordId();
        }

        // Step 5: acquire pessimistic write lock on stream tip (HIST-022: retried on contention)
        SubjectRef subject = resolveSubject(command.subject());
        String streamKey = subject.streamKey();
        Optional<StreamHead> tipOpt = streamHeadPort.lockAndFetchTip(streamKey, command.tenantId());

        // Step 6: derive prevHash and sequenceNo from the stream head (or genesis defaults)
        // Genesis record gets sequenceNo=0 (first record in a new stream, 0-based).
        String prevHash = tipOpt.map(StreamHead::currentHash).orElse(hashChainPort.genesis());
        long sequenceNo = tipOpt.map(head -> head.currentSequenceNo() + 1L).orElse(0L);

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
                        resolvedCorrectsRecordId, // HIST internal UUID, null if no correction
                        false); // erased — always false at write time (P10 / HIST-024)

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
                        resolvedCorrectsRecordId, // HIST internal UUID of corrected record
                        false); // erased — always false at write time (P10 / HIST-024)

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

    /**
     * Validates {@code factType} against the configured vocabulary list ({@code
     * hist.fact-types.allowed}). Adding a type to {@code application.yml} is sufficient — no
     * recompilation required (HIST-023 / SPEC-02).
     *
     * @throws InvalidFactTypeException (HTTP 422) when the type is not in the allowed list
     */
    private void validateFactType(String factType) {
        if (factType == null || !catalogProperties.getAllowed().contains(factType)) {
            throw new InvalidFactTypeException(factType);
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
