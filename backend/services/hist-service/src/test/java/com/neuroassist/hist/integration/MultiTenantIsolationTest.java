package com.neuroassist.hist.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase;
import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.AppendResult;
import com.neuroassist.hist.application.port.in.RecordAuditFactUseCase.Command;
import com.neuroassist.hist.application.port.out.AuditRecordRepositoryPort;
import com.neuroassist.hist.domain.model.AuditRecord;
import com.neuroassist.hist.infrastructure.security.TenantContextHolder;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * INT-HIST-003 — Multi-tenant isolation test (P5, HIST-015).
 *
 * <p>Verifies that a record stored for {@code empresa_a} is invisible to a query scoped to
 * {@code empresa_b}. Isolation is enforced by the tenantId column in every SQL query; this test
 * exercises the end-to-end path through the application service and persistence adapters.
 *
 * <p>Requires: Testcontainers PostgreSQL (inherited from {@link BaseIntegrationTest}).
 */
class MultiTenantIsolationTest extends BaseIntegrationTest {

    private static final String TENANT_A = "empresa_a";
    private static final String TENANT_B = "empresa_b";

    @Autowired
    private RecordAuditFactUseCase recordAuditFactUseCase;

    @Autowired
    private AuditRecordRepositoryPort auditRecordRepositoryPort;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    @Test
    @DisplayName("Record inserted for empresa_a is invisible when queried for empresa_b")
    void auditRecord_forTenantA_notVisibleToTenantB() {
        // Arrange — insert a record for empresa_a
        UUID factId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();
        UUID caregiverId = UUID.randomUUID();

        Command command =
                new Command(
                        factId,
                        "evento.confirmado",
                        new RecordAuditFactUseCase.SubjectCommand("paciente", subjectId),
                        new RecordAuditFactUseCase.ActorCommand(caregiverId, "Test Caregiver"),
                        Map.of("action", "button_press"),
                        Instant.now(),
                        null,
                        TENANT_A);

        // Set TenantContextHolder for the write operation (adapters require it)
        TenantContextHolder.set(TENANT_A);
        AppendResult result = recordAuditFactUseCase.append(command);
        UUID recordId = result.record().id();
        TenantContextHolder.clear();

        // Act — query the record using empresa_b's tenant scope
        TenantContextHolder.set(TENANT_B);
        Optional<AuditRecord> foundForB = auditRecordRepositoryPort.findById(recordId, TENANT_B);
        TenantContextHolder.clear();

        // Assert — empresa_b must not see empresa_a's record
        assertThat(foundForB)
                .as("Record from empresa_a must be invisible to empresa_b query")
                .isEmpty();

        // Sanity check — empresa_a can still see its own record
        TenantContextHolder.set(TENANT_A);
        Optional<AuditRecord> foundForA = auditRecordRepositoryPort.findById(recordId, TENANT_A);
        TenantContextHolder.clear();

        assertThat(foundForA)
                .as("empresa_a should still see its own record")
                .isPresent();
        assertThat(foundForA.get().tenantId()).isEqualTo(TENANT_A);
    }
}
