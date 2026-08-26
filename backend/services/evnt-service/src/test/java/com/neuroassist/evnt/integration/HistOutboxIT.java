package com.neuroassist.evnt.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

import com.neuroassist.evnt.application.port.in.HistOutboxDrainUseCase;
import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.infrastructure.clients.AuditTrailClient;
import com.neuroassist.evnt.infrastructure.persistence.entity.HistOutboxJpaEntity;
import com.neuroassist.evnt.infrastructure.persistence.repository.HistOutboxJpaRepository;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * E2E-017 — the fact survives a hist-service outage, against a real PostgreSQL.
 *
 * <p>This is the test the previous implementation could not have had: the adapter logged the loss
 * and returned, so there was no row to look for.
 *
 * <p>It runs against the real table on purpose. The audit outbox of pac-service was written
 * correctly and still failed every INSERT in production, because the migration declared {@code
 * status} as a native ENUM while the entity mapped a {@code String} — invisible to a suite where
 * Hibernate creates the schema (defect C-12). Exercising the mapping is the point.
 */
@DisplayName("Outbox de auditoria do EVNT contra PostgreSQL real")
class HistOutboxIT extends BaseIntegrationTest {

    private static final String TENANT = "t_emp_test";

    @MockitoBean private AuditTrailClient auditTrailClient;

    @Autowired private AuditTrailPort auditTrailPort;
    @Autowired private HistOutboxDrainUseCase drainUseCase;
    @Autowired private HistOutboxJpaRepository outboxRepository;
    @Autowired private TransactionTemplate transactionTemplate;

    private final UUID factId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        TenantContextHolder.set(TENANT);
        transactionTemplate.executeWithoutResult(s -> outboxRepository.deleteAll());
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    private AuditTrailPort.AuditFact anAlarmFact() {
        return new AuditTrailPort.AuditFact(
                factId,
                "evento.criado",
                "paciente",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "Cuidadora Dev",
                Map.of("urgencia", "emergencia"),
                Instant.parse("2026-08-23T12:00:00Z"));
    }

    private List<HistOutboxJpaEntity> allEntries() {
        return transactionTemplate.execute(s -> outboxRepository.findAll());
    }

    @Test
    @DisplayName("HIST fora do ar → fato fica pendente; HIST volta → entregue no tick seguinte")
    void factSurvivesAnOutageAndIsDeliveredOnRecovery() {
        doThrow(new RuntimeException("connection refused"))
                .when(auditTrailClient)
                .recordFact(any());

        auditTrailPort.record(anAlarmFact());

        List<HistOutboxJpaEntity> pending = allEntries();
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).getStatus()).isEqualTo("pending");
        assertThat(pending.get(0).getFactId()).isEqualTo(factId);
        assertThat(pending.get(0).getFactType()).isEqualTo("evento.criado");

        // hist-service volta.
        reset(auditTrailClient);
        when(auditTrailClient.recordFact(any())).thenReturn(null);

        HistOutboxDrainUseCase.DrainResult result = drainUseCase.drainCurrentTenant();

        assertThat(result.delivered()).isEqualTo(1);
        assertThat(allEntries().get(0).getStatus()).isEqualTo("sent");
        assertThat(allEntries().get(0).getSentAt()).isNotNull();
    }

    @Test
    @DisplayName("entrega direta bem-sucedida não deixa nada no outbox")
    void successfulDeliveryLeavesNothingBehind() {
        when(auditTrailClient.recordFact(any())).thenReturn(null);

        auditTrailPort.record(anAlarmFact());

        assertThat(allEntries()).isEmpty();
    }

    @Test
    @DisplayName("o mesmo fato não é enfileirado duas vezes")
    void theSameFactIsNeverQueuedTwice() {
        doThrow(new RuntimeException("connection refused"))
                .when(auditTrailClient)
                .recordFact(any());

        auditTrailPort.record(anAlarmFact());
        auditTrailPort.record(anAlarmFact()); // mesma factId — retentativa do chamador

        // Enfileirar duas vezes não duplicaria o fato no HIST (a deduplicação por factId cuidaria
        // disso), e é justamente por isso que precisa ser barrado aqui: o HIST esconderia o
        // defeito em vez de o denunciar.
        assertThat(allEntries()).hasSize(1);
    }
}
