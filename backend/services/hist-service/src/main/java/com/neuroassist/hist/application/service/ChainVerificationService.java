package com.neuroassist.hist.application.service;

import com.neuroassist.hist.application.port.in.ChainVerificationUseCase;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.application.port.out.HashChainPort;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.ChainStatus;
import com.neuroassist.hist.domain.model.ChainVerificationResult;
import com.neuroassist.hist.infrastructure.config.HistMetrics;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service — hash-chain verification for one (tenant, subject) stream (HIST-008, P2).
 *
 * <p>Algorithm:
 *
 * <ol>
 *   <li>Derive streamKey from subjectType:subjectId.
 *   <li>Fetch all records ordered by sequenceNo ascending.
 *   <li>If no records → return {@code empty}.
 *   <li>Walk from genesis: for each record verify sequenceNo is consecutive, {@code prevHash}
 *       matches expected, and recomputed {@code recordHash} matches stored.
 *   <li>Any mismatch → return {@code broken} at the failing sequenceNo.
 *   <li>All pass → return {@code intact}.
 * </ol>
 *
 * <p>Read-only — never mutates any record (ADR-004). {@code @Transactional(readOnly = true)} only
 * here (RULE-03).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChainVerificationService implements ChainVerificationUseCase {

    private final AuditRecordRepositoryPort auditRecordRepositoryPort;
    private final HashChainPort hashChainPort;
    private final HistMetrics histMetrics;

    @Override
    @Transactional(readOnly = true)
    public ChainVerificationResult verifyStream(VerificationCommand command) {
        String streamKey = command.subjectType() + ":" + command.subjectId();
        String tenantId = command.tenantId();

        List<AuditRecord> records =
                auditRecordRepositoryPort.findByStreamKeyOrderedBySequenceAsc(streamKey, tenantId);

        if (records.isEmpty()) {
            log.info(
                    "Chain verification: empty stream streamKey={} tenant={}", streamKey, tenantId);
            histMetrics.incrementChainVerificationTotal(tenantId, "empty");
            return new ChainVerificationResult(
                    streamKey, ChainStatus.empty, 0, 0, null, Instant.now());
        }

        String expectedPrevHash = hashChainPort.genesis();
        long expectedSequenceNo = 0L; // first record has seq=0 (0-based)
        int verifiedCount = 0;
        int erasedCount = 0;

        for (AuditRecord record : records) {
            // Check consecutive sequenceNo — no gaps allowed
            if (record.sequenceNo() != expectedSequenceNo) {
                log.warn(
                        "Chain broken: sequenceNo gap at expected={} found={} streamKey={} tenant={}",
                        expectedSequenceNo,
                        record.sequenceNo(),
                        streamKey,
                        tenantId);
                histMetrics.incrementChainVerificationTotal(tenantId, "broken");
                return new ChainVerificationResult(
                        streamKey, ChainStatus.broken, verifiedCount, erasedCount,
                        expectedSequenceNo, Instant.now());
            }

            // Check prevHash linkage — verifiable even for erased records (stored recordHash intact)
            if (!expectedPrevHash.equals(record.prevHash())) {
                log.warn(
                        "Chain broken: prevHash mismatch at sequenceNo={} streamKey={} tenant={}",
                        record.sequenceNo(),
                        streamKey,
                        tenantId);
                histMetrics.incrementChainVerificationTotal(tenantId, "broken");
                return new ChainVerificationResult(
                        streamKey, ChainStatus.broken, verifiedCount, erasedCount,
                        record.sequenceNo(), Instant.now());
            }

            // If patient key was erased (LGPD crypto-shredding), skip hash re-computation.
            // The chain structure (prevHash linkage) is still verified above — only the payload
            // content integrity cannot be re-checked after erasure.
            if (record.erased()) {
                log.debug(
                        "LGPD-erased record at sequenceNo={} streamKey={} — skipping hash re-computation",
                        record.sequenceNo(), streamKey);
                erasedCount++;
                expectedPrevHash = record.recordHash();
                expectedSequenceNo++;
                continue;
            }

            // Recompute recordHash and compare with stored value
            String recomputed = hashChainPort.compute(expectedPrevHash, record);
            if (!recomputed.equals(record.recordHash())) {
                log.warn(
                        "Chain broken: recordHash mismatch at sequenceNo={} streamKey={} tenant={}",
                        record.sequenceNo(),
                        streamKey,
                        tenantId);
                histMetrics.incrementChainVerificationTotal(tenantId, "broken");
                return new ChainVerificationResult(
                        streamKey, ChainStatus.broken, verifiedCount, erasedCount,
                        record.sequenceNo(), Instant.now());
            }

            // Record successfully verified
            verifiedCount++;
            expectedPrevHash = record.recordHash();
            expectedSequenceNo++;
        }

        ChainStatus finalStatus = erasedCount > 0 ? ChainStatus.partially_erased : ChainStatus.intact;
        String outcome = erasedCount > 0 ? "partially_erased" : "ok";
        log.info(
                "Chain verification {}: streamKey={} tenant={} verified={} erased={}",
                finalStatus, streamKey, tenantId, verifiedCount, erasedCount);
        histMetrics.incrementChainVerificationTotal(tenantId, outcome);
        return new ChainVerificationResult(
                streamKey, finalStatus, verifiedCount, erasedCount, null, Instant.now());
    }
}
