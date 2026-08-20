package com.neuroassist.hist.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.AppendResult;
import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.Command;
import com.neuroassist.hist.application.port.out.AuditLedgerPort;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.application.port.out.HashChainPort;
import com.neuroassist.hist.application.port.out.IdempotencyPort;
import com.neuroassist.hist.application.port.out.StreamHeadPort;
import com.neuroassist.hist.domain.model.ActorRef;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.domain.model.ForbiddenPayloadException;
import com.neuroassist.hist.domain.model.HashChain;
import com.neuroassist.hist.domain.model.IdempotencyEntry;
import com.neuroassist.hist.domain.model.OccurredAtInFutureException;
import com.neuroassist.hist.domain.model.StreamHead;
import com.neuroassist.hist.domain.model.SubjectRef;
import com.neuroassist.hist.domain.model.SubjectType;
import com.neuroassist.hist.domain.model.UnknownFactTypeException;
import com.neuroassist.hist.infrastructure.config.HistMetrics;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link RecordAuditFactService} — HIST-006, P2 acceptance criteria.
 *
 * <p>All outbound ports are mocked — no Spring context, no database (RULE-03). Criteria:
 *
 * <ul>
 *   <li>Append new fact → returns AppendResult(record, created=true), 201 semantics.
 *   <li>Replay duplicate factId → returns AppendResult(original, created=false), 200 semantics.
 *   <li>Unknown factType → UnknownFactTypeException (422).
 *   <li>Forbidden payload key → ForbiddenPayloadException (422).
 *   <li>occurredAt in the future > 5 min → OccurredAtInFutureException (422).
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class RecordAuditFactServiceTest {

    @Mock private IdempotencyPort idempotencyPort;
    @Mock private StreamHeadPort streamHeadPort;
    @Mock private HashChainPort hashChainPort;
    @Mock private AuditLedgerPort auditLedgerPort;
    @Mock private AuditRecordRepositoryPort auditRecordRepositoryPort;
    @Mock private HistMetrics histMetrics;

    // Domain service — real instance (pure POJO, no Spring context needed)
    private final com.neuroassist.hist.domain.service.LgpdPayloadGuard lgpdPayloadGuard =
            new com.neuroassist.hist.domain.service.LgpdPayloadGuard();

    private RecordAuditFactService service;

    private static final String TENANT_ID = "empresa-test";
    private static final UUID FACT_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID SUBJECT_ID = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001");
    private static final UUID CAREGIVER_ID =
            UUID.fromString("cccccccc-0000-0000-0000-000000000001");
    private static final String FACT_TYPE = "evento.confirmado";
    private static final String COMPUTED_HASH = "deadbeef" + "0".repeat(56);

    @BeforeEach
    void setUp() {
        // HistMetrics mock — Timer.start() needs a non-null Timer to stop against
        io.micrometer.core.instrument.Timer timerMock =
                org.mockito.Mockito.mock(io.micrometer.core.instrument.Timer.class);
        org.mockito.Mockito.when(histMetrics.ingestionLatencyTimer(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(timerMock);

        service =
                new RecordAuditFactService(
                        idempotencyPort,
                        streamHeadPort,
                        hashChainPort,
                        auditLedgerPort,
                        auditRecordRepositoryPort,
                        lgpdPayloadGuard,
                        histMetrics);
    }

    @Test
    @DisplayName("append new fact → persists record, returns created=true")
    void append_newFact_createsRecord() {
        // Arrange
        Command command = buildCommand(FACT_ID, Instant.now());

        when(idempotencyPort.findByFactId(FACT_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(streamHeadPort.lockAndFetchTip(anyString(), eq(TENANT_ID)))
                .thenReturn(Optional.empty());
        when(hashChainPort.genesis()).thenReturn(HashChain.GENESIS_PREV_HASH);
        when(hashChainPort.compute(anyString(), any(AuditRecord.class))).thenReturn(COMPUTED_HASH);
        when(auditLedgerPort.insert(any(AuditRecord.class), eq(TENANT_ID)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        AppendResult result = service.append(command);

        // Assert
        assertThat(result.created()).isTrue();
        assertThat(result.record()).isNotNull();
        assertThat(result.record().tenantId()).isEqualTo(TENANT_ID);
        assertThat(result.record().factId()).isEqualTo(FACT_ID);
        assertThat(result.record().factType()).isEqualTo(FACT_TYPE);
        assertThat(result.record().recordHash()).isEqualTo(COMPUTED_HASH);
        assertThat(result.record().sequenceNo()).isEqualTo(1L);
        assertThat(result.record().prevHash()).isEqualTo(HashChain.GENESIS_PREV_HASH);

        verify(auditLedgerPort).insert(any(AuditRecord.class), eq(TENANT_ID));
        verify(idempotencyPort).record(any(IdempotencyEntry.class), eq(TENANT_ID));
        verify(streamHeadPort).upsert(any(StreamHead.class), eq(TENANT_ID));
    }

    @Test
    @DisplayName("append existing stream → sequenceNo increments from stream head")
    void append_existingStream_incrementsSequenceNo() {
        // Arrange
        Command command = buildCommand(FACT_ID, Instant.now());
        String prevHash = "abc123" + "0".repeat(58);
        StreamHead existingHead =
                new StreamHead(
                        "paciente:" + SUBJECT_ID,
                        TENANT_ID,
                        5L,
                        prevHash,
                        Instant.now().minusSeconds(10));

        when(idempotencyPort.findByFactId(FACT_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(streamHeadPort.lockAndFetchTip(anyString(), eq(TENANT_ID)))
                .thenReturn(Optional.of(existingHead));
        when(hashChainPort.genesis()).thenReturn(HashChain.GENESIS_PREV_HASH);
        when(hashChainPort.compute(eq(prevHash), any(AuditRecord.class))).thenReturn(COMPUTED_HASH);
        when(auditLedgerPort.insert(any(AuditRecord.class), eq(TENANT_ID)))
                .thenAnswer(inv -> inv.getArgument(0));

        // Act
        AppendResult result = service.append(command);

        // Assert
        assertThat(result.created()).isTrue();
        assertThat(result.record().sequenceNo()).isEqualTo(6L); // 5 + 1
        assertThat(result.record().prevHash()).isEqualTo(prevHash);
    }

    @Test
    @DisplayName("append duplicate factId → returns original record, created=false (ADR-005)")
    void append_duplicateFactId_returnsOriginalRecord() {
        // Arrange
        UUID originalRecordId = UUID.randomUUID();
        IdempotencyEntry existingEntry =
                new IdempotencyEntry(
                        FACT_ID, TENANT_ID, originalRecordId, Instant.now().minusSeconds(60));
        AuditRecord originalRecord = buildStoredRecord(originalRecordId);

        when(idempotencyPort.findByFactId(FACT_ID, TENANT_ID))
                .thenReturn(Optional.of(existingEntry));
        when(auditRecordRepositoryPort.findById(originalRecordId, TENANT_ID))
                .thenReturn(Optional.of(originalRecord));

        Command command = buildCommand(FACT_ID, Instant.now());

        // Act
        AppendResult result = service.append(command);

        // Assert
        assertThat(result.created()).isFalse();
        assertThat(result.record().id()).isEqualTo(originalRecordId);
        assertThat(result.record().factId()).isEqualTo(FACT_ID);

        // No new ledger insert, no stream head update
        verify(auditLedgerPort, never()).insert(any(), any());
        verify(streamHeadPort, never()).upsert(any(), any());
        verify(idempotencyPort, never()).record(any(), any());
    }

    @Test
    @DisplayName("unknown factType → UnknownFactTypeException (422)")
    void append_unknownFactType_throws() {
        Command command = buildCommandWithFactType("unknown.type");

        assertThatThrownBy(() -> service.append(command))
                .isInstanceOf(UnknownFactTypeException.class)
                .hasMessageContaining("unknown.type");
    }

    @Test
    @DisplayName("payload with forbidden biometric key → ForbiddenPayloadException (422)")
    void append_forbiddenPayloadKey_throws() {
        Command command = buildCommandWithPayload(Map.of("image", "base64-data"));

        assertThatThrownBy(() -> service.append(command))
                .isInstanceOf(ForbiddenPayloadException.class);
    }

    @Test
    @DisplayName("occurredAt more than 5 min in the future → OccurredAtInFutureException (422)")
    void append_occurredAtTooFarInFuture_throws() {
        Instant farFuture = Instant.now().plusSeconds(400); // 6+ minutes ahead
        Command command = buildCommand(FACT_ID, farFuture);

        assertThatThrownBy(() -> service.append(command))
                .isInstanceOf(OccurredAtInFutureException.class);
    }

    @Test
    @DisplayName("occurredAt just within 5 min drift → accepted")
    void append_occurredAtWithinDrift_accepted() {
        Instant withinDrift = Instant.now().plusSeconds(240); // 4 min ahead — within limit
        Command command = buildCommand(FACT_ID, withinDrift);

        when(idempotencyPort.findByFactId(FACT_ID, TENANT_ID)).thenReturn(Optional.empty());
        when(streamHeadPort.lockAndFetchTip(anyString(), eq(TENANT_ID)))
                .thenReturn(Optional.empty());
        when(hashChainPort.genesis()).thenReturn(HashChain.GENESIS_PREV_HASH);
        when(hashChainPort.compute(anyString(), any())).thenReturn(COMPUTED_HASH);
        when(auditLedgerPort.insert(any(), eq(TENANT_ID))).thenAnswer(inv -> inv.getArgument(0));

        AppendResult result = service.append(command);

        assertThat(result.created()).isTrue();
    }

    // ---- helpers ----

    private Command buildCommand(UUID factId, Instant occurredAt) {
        return new Command(
                factId,
                FACT_TYPE,
                new com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.SubjectCommand(
                        "paciente", SUBJECT_ID),
                new com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.ActorCommand(
                        CAREGIVER_ID, "Cuidador Teste"),
                Map.of("nivel", "medio"),
                occurredAt,
                null,
                TENANT_ID);
    }

    private Command buildCommandWithFactType(String factType) {
        return new Command(
                FACT_ID,
                factType,
                new com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.SubjectCommand(
                        "paciente", SUBJECT_ID),
                new com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.ActorCommand(
                        CAREGIVER_ID, "Cuidador Teste"),
                Map.of("nivel", "alto"),
                Instant.now(),
                null,
                TENANT_ID);
    }

    private Command buildCommandWithPayload(Map<String, Object> payload) {
        return new Command(
                FACT_ID,
                FACT_TYPE,
                new com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.SubjectCommand(
                        "paciente", SUBJECT_ID),
                new com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.ActorCommand(
                        CAREGIVER_ID, "Cuidador Teste"),
                payload,
                Instant.now(),
                null,
                TENANT_ID);
    }

    private AuditRecord buildStoredRecord(UUID id) {
        return new AuditRecord(
                id,
                TENANT_ID,
                FACT_ID,
                FACT_TYPE,
                new SubjectRef(SubjectType.paciente, SUBJECT_ID),
                new ActorRef(CAREGIVER_ID, "Cuidador Teste"),
                Map.of("nivel", "medio"),
                Instant.now().minusSeconds(70),
                Instant.now().minusSeconds(60),
                "paciente:" + SUBJECT_ID,
                1L,
                HashChain.GENESIS_PREV_HASH,
                COMPUTED_HASH,
                null);
    }
}
