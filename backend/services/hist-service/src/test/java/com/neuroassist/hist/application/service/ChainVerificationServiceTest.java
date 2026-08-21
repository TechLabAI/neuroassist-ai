package com.neuroassist.hist.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.neuroassist.hist.application.port.in.ChainVerificationUseCase.VerificationCommand;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.application.port.out.HashChainPort;
import com.neuroassist.hist.domain.model.ActorRef;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.ChainStatus;
import com.neuroassist.hist.domain.model.ChainVerificationResult;
import com.neuroassist.hist.domain.model.HashChain;
import com.neuroassist.hist.domain.model.SubjectRef;
import com.neuroassist.hist.domain.model.SubjectType;
import com.neuroassist.hist.infrastructure.config.HistMetrics;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link ChainVerificationService} — HIST-008, P2 acceptance criteria.
 *
 * <p>All outbound ports are mocked — pure unit test. Criteria:
 *
 * <ul>
 *   <li>Empty stream → status=empty, recordsVerified=0.
 *   <li>N records with valid hashes → status=intact, recordsVerified=N.
 *   <li>1 tampered record → status=broken at that sequenceNo.
 *   <li>prevHash linkage broken → status=broken.
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ChainVerificationServiceTest {

    @Mock private AuditRecordRepositoryPort auditRecordRepositoryPort;
    @Mock private HashChainPort hashChainPort;
    @Mock private HistMetrics histMetrics;

    private ChainVerificationService service;

    private static final String TENANT_ID = "empresa-test";
    private static final UUID SUBJECT_ID = UUID.fromString("dddddddd-0000-0000-0000-000000000001");
    private static final String STREAM_KEY = "paciente:" + SUBJECT_ID;
    private static final String GENESIS = HashChain.GENESIS_PREV_HASH;

    @BeforeEach
    void setUp() {
        service =
                new ChainVerificationService(auditRecordRepositoryPort, hashChainPort, histMetrics);
    }

    @Test
    @DisplayName("empty stream → ChainStatus.empty, recordsVerified=0")
    void verify_emptyStream_returnsEmpty() {
        when(auditRecordRepositoryPort.findByStreamKeyOrderedBySequenceAsc(STREAM_KEY, TENANT_ID))
                .thenReturn(List.of());

        ChainVerificationResult result = service.verifyStream(buildCommand());

        assertThat(result.status()).isEqualTo(ChainStatus.empty);
        assertThat(result.recordsVerified()).isEqualTo(0);
        assertThat(result.brokenAtSequenceNo()).isNull();
        assertThat(result.streamKey()).isEqualTo(STREAM_KEY);
    }

    @Test
    @DisplayName("N records with valid hash chain → status=intact, recordsVerified=N")
    void verify_intactChain_returnsIntact() {
        // Build a chain of 3 records with consistent hashes (0-based sequencing)
        String hash0 = "aaa" + "0".repeat(61);
        String hash1 = "bbb" + "0".repeat(61);
        String hash2 = "ccc" + "0".repeat(61);

        List<AuditRecord> records =
                List.of(
                        buildRecord(0L, GENESIS, hash0),
                        buildRecord(1L, hash0, hash1),
                        buildRecord(2L, hash1, hash2));

        when(auditRecordRepositoryPort.findByStreamKeyOrderedBySequenceAsc(STREAM_KEY, TENANT_ID))
                .thenReturn(records);
        when(hashChainPort.genesis()).thenReturn(GENESIS);

        // Mock recomputation: adapter returns the same hash as stored (= valid chain)
        when(hashChainPort.compute(eq(GENESIS), eq(records.get(0)))).thenReturn(hash0);
        when(hashChainPort.compute(eq(hash0), eq(records.get(1)))).thenReturn(hash1);
        when(hashChainPort.compute(eq(hash1), eq(records.get(2)))).thenReturn(hash2);

        ChainVerificationResult result = service.verifyStream(buildCommand());

        assertThat(result.status()).isEqualTo(ChainStatus.intact);
        assertThat(result.recordsVerified()).isEqualTo(3);
        assertThat(result.brokenAtSequenceNo()).isNull();
    }

    @Test
    @DisplayName("single record in new stream → status=intact, recordsVerified=1")
    void verify_singleRecord_returnsIntact() {
        String hash0 = "111" + "0".repeat(61);
        AuditRecord record = buildRecord(0L, GENESIS, hash0);

        when(auditRecordRepositoryPort.findByStreamKeyOrderedBySequenceAsc(STREAM_KEY, TENANT_ID))
                .thenReturn(List.of(record));
        when(hashChainPort.genesis()).thenReturn(GENESIS);
        when(hashChainPort.compute(GENESIS, record)).thenReturn(hash0);

        ChainVerificationResult result = service.verifyStream(buildCommand());

        assertThat(result.status()).isEqualTo(ChainStatus.intact);
        assertThat(result.recordsVerified()).isEqualTo(1);
    }

    @Test
    @DisplayName("record with tampered recordHash → status=broken at that sequenceNo")
    void verify_tamperedRecordHash_returnsBroken() {
        String hash0 = "aaa" + "0".repeat(61);
        String hash1Stored = "bbb" + "0".repeat(61); // stored (tampered)
        String hash1Recomputed = "fff" + "0".repeat(61); // recomputed (different)

        List<AuditRecord> records =
                List.of(
                        buildRecord(0L, GENESIS, hash0),
                        buildRecord(1L, hash0, hash1Stored)); // record 1 has tampered hash

        when(auditRecordRepositoryPort.findByStreamKeyOrderedBySequenceAsc(STREAM_KEY, TENANT_ID))
                .thenReturn(records);
        when(hashChainPort.genesis()).thenReturn(GENESIS);
        when(hashChainPort.compute(GENESIS, records.get(0))).thenReturn(hash0);
        when(hashChainPort.compute(hash0, records.get(1))).thenReturn(hash1Recomputed); // mismatch!

        ChainVerificationResult result = service.verifyStream(buildCommand());

        assertThat(result.status()).isEqualTo(ChainStatus.broken);
        assertThat(result.brokenAtSequenceNo()).isEqualTo(1L);
        assertThat(result.recordsVerified()).isEqualTo(1); // only record 0 verified before failure
    }

    @Test
    @DisplayName("record with wrong prevHash linkage → status=broken at that sequenceNo")
    void verify_brokenPrevHashLink_returnsBroken() {
        String hash0 = "aaa" + "0".repeat(61);
        String wrongPrevHash = "zzz" + "0".repeat(61); // record 1 points to wrong prev

        List<AuditRecord> records =
                List.of(
                        buildRecord(0L, GENESIS, hash0),
                        buildRecord(1L, wrongPrevHash, "ddd" + "0".repeat(61))); // broken link

        when(auditRecordRepositoryPort.findByStreamKeyOrderedBySequenceAsc(STREAM_KEY, TENANT_ID))
                .thenReturn(records);
        when(hashChainPort.genesis()).thenReturn(GENESIS);
        when(hashChainPort.compute(GENESIS, records.get(0))).thenReturn(hash0);

        ChainVerificationResult result = service.verifyStream(buildCommand());

        assertThat(result.status()).isEqualTo(ChainStatus.broken);
        assertThat(result.brokenAtSequenceNo()).isEqualTo(1L);
    }

    // ---- helpers ----

    private VerificationCommand buildCommand() {
        return new VerificationCommand("paciente", SUBJECT_ID, null, null, TENANT_ID);
    }

    private AuditRecord buildRecord(long sequenceNo, String prevHash, String recordHash) {
        return new AuditRecord(
                UUID.randomUUID(),
                TENANT_ID,
                UUID.randomUUID(),
                "evento.confirmado",
                new SubjectRef(SubjectType.paciente, SUBJECT_ID),
                new ActorRef(UUID.randomUUID(), "Cuidador"),
                Map.of("nivel", "alto"),
                Instant.now().minusSeconds(120),
                Instant.now().minusSeconds(100),
                STREAM_KEY,
                sequenceNo,
                prevHash,
                recordHash,
                null,
                false);
    }
}
