package com.neuroassist.pac.infrastructure.clients;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.pac.application.port.out.HistFactPort;
import com.neuroassist.pac.domain.model.HistFact;
import com.neuroassist.pac.infrastructure.persistence.entity.HistOutboxJpaEntity;
import com.neuroassist.pac.infrastructure.persistence.repository.HistOutboxJpaRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * INT-PAC-004 — Adapter implementing {@link HistFactPort}.
 *
 * <p>Resilient recording strategy:
 *
 * <ol>
 *   <li>Always write to {@code hist_outbox} table WITHIN the caller's {@code @Transactional}
 *       boundary — guarantees the fact is durably recorded even if hist-service is unavailable.
 *   <li>Immediately attempt direct delivery via {@link HistFactClient}.
 *   <li>On success: update outbox entry to {@code sent}.
 *   <li>On failure: log the error; the outbox entry remains {@code pending} for retry.
 * </ol>
 *
 * <p>This method NEVER propagates exceptions — any delivery failure is absorbed, satisfying the
 * acceptance criterion: "HIST indisponível: lifecycle commitado, fato no outbox (sem rollback)".
 *
 * <p>Hexagonal rules:
 *
 * <ul>
 *   <li>RULE-06: the {@link HistFactClient} {@code @FeignClient} is declared in this package.
 *   <li>RULE-03: no {@code @Transactional} here — caller's boundary applies.
 *   <li>RULE-04/05: no {@code api.*} dependency.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistFactAdapter implements HistFactPort {

    private final HistFactClient histFactClient;
    private final HistOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void record(HistFact fact) {
        HistFactRequest request = toRequest(fact);

        // Step 1: Persist to outbox within the caller's transaction (atomic durability)
        HistOutboxJpaEntity outboxEntry = persistToOutbox(fact.eventType(), request);

        // Step 2: Attempt direct delivery to hist-service (best-effort, never throws)
        try {
            histFactClient.recordFact(request);
            // Step 3: Mark as sent (still within the same transaction)
            outboxEntry.setStatus("sent");
            outboxEntry.setSentAt(Instant.now());
            outboxRepository.save(outboxEntry);
            log.debug(
                    "HistFact delivered directly: eventType={} comandoId={} outboxId={}",
                    fact.eventType(),
                    fact.comandoId(),
                    outboxEntry.getId());
        } catch (Exception ex) {
            // Step 4: Leave outbox entry as 'pending' — retry will handle it later
            log.warn(
                    "HistFact direct delivery failed, outbox entry pending: "
                            + "eventType={} comandoId={} outboxId={} error={}",
                    fact.eventType(),
                    fact.comandoId(),
                    outboxEntry.getId(),
                    ex.getMessage());
            // Do NOT rethrow — this method contract is resilient (never blocks lifecycle)
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private HistOutboxJpaEntity persistToOutbox(String eventType, HistFactRequest request) {
        String payloadJson;
        try {
            payloadJson = objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException ex) {
            // Fallback: store minimal JSON to preserve the fact type
            log.error(
                    "Failed to serialize HistFactRequest for outbox eventType={}: {}",
                    eventType,
                    ex.getMessage());
            payloadJson = "{\"eventType\":\"" + eventType + "\",\"serializationError\":true}";
        }

        HistOutboxJpaEntity entry =
                HistOutboxJpaEntity.builder()
                        .eventType(eventType)
                        .payloadJson(payloadJson)
                        .status("pending")
                        .retryCount(0)
                        .createdAt(Instant.now())
                        .build();

        return outboxRepository.save(entry);
    }

    private HistFactRequest toRequest(HistFact fact) {
        return new HistFactRequest(
                fact.eventType(),
                fact.comandoId(),
                fact.pacienteId(),
                fact.tenantId(),
                fact.urgencia(),
                fact.calibracaoVersao(),
                fact.estado(),
                fact.occurredAt());
    }
}
