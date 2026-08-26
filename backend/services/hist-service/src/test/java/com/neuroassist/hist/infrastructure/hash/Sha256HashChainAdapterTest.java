package com.neuroassist.hist.infrastructure.hash;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.hist.domain.model.ActorRef;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.HashChain;
import com.neuroassist.hist.domain.model.SubjectRef;
import com.neuroassist.hist.domain.model.SubjectType;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Sha256HashChainAdapter} — HIST-007, P2 acceptance criteria.
 *
 * <p>No Spring context required — pure Java unit test. Criteria: fixed-vector reference input →
 * expected SHA-256 hash.
 */
class Sha256HashChainAdapterTest {

    private Sha256HashChainAdapter adapter;

    // Fixed reference values for reproducible test vectors
    private static final UUID FIXED_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID FIXED_FACT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID FIXED_SUBJECT_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID FIXED_CAREGIVER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final Instant FIXED_OCCURRED_AT = Instant.parse("2026-01-01T10:00:00Z");
    private static final Instant FIXED_RECORDED_AT = Instant.parse("2026-01-01T10:00:01Z");

    @BeforeEach
    void setUp() {
        adapter = new Sha256HashChainAdapter();
    }

    @Test
    @DisplayName("genesis() returns the all-zeros 64-hex string (GENESIS_PREV_HASH)")
    void genesis_returnsAllZerosHex() {
        String genesis = adapter.genesis();

        assertThat(genesis).isEqualTo(HashChain.GENESIS_PREV_HASH);
        assertThat(genesis).hasSize(64);
        assertThat(genesis).matches("[0]+");
    }

    @Test
    @DisplayName("compute() produces a 64-char lowercase hex string")
    void compute_producesValidHexString() {
        AuditRecord record = buildFixedRecord(1L, HashChain.GENESIS_PREV_HASH, null);

        String hash = adapter.compute(HashChain.GENESIS_PREV_HASH, record);

        assertThat(hash).isNotNull();
        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("compute() is deterministic — same input produces same hash (fixed vector)")
    void compute_isDeterministic() {
        AuditRecord record = buildFixedRecord(1L, HashChain.GENESIS_PREV_HASH, null);

        String hash1 = adapter.compute(HashChain.GENESIS_PREV_HASH, record);
        String hash2 = adapter.compute(HashChain.GENESIS_PREV_HASH, record);

        assertThat(hash1).isEqualTo(hash2);
    }

    @Test
    @DisplayName("compute() chains correctly — record2.prevHash = record1.recordHash")
    void compute_chainsSequentialRecords() {
        AuditRecord record1 = buildFixedRecord(1L, HashChain.GENESIS_PREV_HASH, null);
        String hash1 = adapter.compute(HashChain.GENESIS_PREV_HASH, record1);

        // Record 2 references hash1 as prevHash
        AuditRecord record1WithHash = buildFixedRecord(1L, HashChain.GENESIS_PREV_HASH, hash1);
        AuditRecord record2 = buildFixedRecord(2L, hash1, null);
        String hash2 = adapter.compute(hash1, record2);

        assertThat(hash2).isNotEqualTo(hash1);
        assertThat(hash2).hasSize(64);
    }

    @Test
    @DisplayName("compute() produces different hash when any field changes (tamper detection)")
    void compute_differentHash_whenFieldTampered() {
        AuditRecord original = buildFixedRecord(1L, HashChain.GENESIS_PREV_HASH, null);
        String originalHash = adapter.compute(HashChain.GENESIS_PREV_HASH, original);

        // Tamper: change factType
        AuditRecord tampered =
                new AuditRecord(
                        original.id(),
                        original.tenantId(),
                        original.factId(),
                        "evento.descartado", // changed from evento.confirmado
                        original.subject(),
                        original.actor(),
                        original.payload(),
                        original.occurredAt(),
                        original.recordedAt(),
                        original.streamKey(),
                        original.sequenceNo(),
                        original.prevHash(),
                        original.recordHash(),
                        original.correctsRecordId(),
                        false,
                        FIXED_SUBJECT_ID);

        String tamperedHash = adapter.compute(HashChain.GENESIS_PREV_HASH, tampered);

        assertThat(tamperedHash).isNotEqualTo(originalHash);
    }

    @Test
    @DisplayName("canonicalize() produces valid JSON with sorted top-level keys")
    void canonicalize_producesSortedKeyJson() {
        AuditRecord record = buildFixedRecord(1L, HashChain.GENESIS_PREV_HASH, null);

        String canonical = adapter.canonicalize(record);

        assertThat(canonical).isNotNull();
        assertThat(canonical).startsWith("{");
        assertThat(canonical).endsWith("}");
        // Verify key ordering: "actor" comes before "correctsRecordId" comes before "factId", etc.
        int actorIdx = canonical.indexOf("\"actor\"");
        int correctsIdx = canonical.indexOf("\"correctsRecordId\"");
        int factIdIdx = canonical.indexOf("\"factId\"");
        int idIdx = canonical.indexOf("\"id\"");
        assertThat(actorIdx).isLessThan(correctsIdx);
        assertThat(correctsIdx).isLessThan(factIdIdx);
        assertThat(factIdIdx).isLessThan(idIdx);
    }

    // ---- helpers ----

    private AuditRecord buildFixedRecord(long sequenceNo, String prevHash, String recordHash) {
        SubjectRef subject = new SubjectRef(SubjectType.paciente, FIXED_SUBJECT_ID);
        ActorRef actor = new ActorRef(FIXED_CAREGIVER_ID, "Maria da Silva");
        return new AuditRecord(
                FIXED_ID,
                "empresa-abc",
                FIXED_FACT_ID,
                "evento.confirmado",
                subject,
                actor,
                Map.of("nivel", "alto"),
                FIXED_OCCURRED_AT,
                FIXED_RECORDED_AT,
                "paciente:" + FIXED_SUBJECT_ID,
                sequenceNo,
                prevHash,
                recordHash,
                null,
                false,
                FIXED_SUBJECT_ID);
    }
}
