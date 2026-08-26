package com.neuroassist.evnt.infrastructure.clients.adapter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.evnt.application.port.out.HistFactRedeliveryPort;
import com.neuroassist.evnt.infrastructure.clients.AuditTrailClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redelivers a stored outbox payload to hist-service (E2E-017).
 *
 * <p>Deserialises the JSON back into the very request that was attempted originally — same {@code
 * factId} above all, which is what makes hist-service treat the redelivery as a replay (200) rather
 * than a second fact (201).
 *
 * <p>Never throws: the drain loop decides what a failure means, and an exception escaping here
 * would abort a batch mid-way with some entries already marked sent.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HistFactRedeliveryAdapter implements HistFactRedeliveryPort {

    private final AuditTrailClient client;
    private final ObjectMapper objectMapper;

    @Override
    public boolean redeliver(String payloadJson) {
        AuditTrailClient.RecordAuditFactRequest request;
        try {
            request =
                    objectMapper.readValue(
                            payloadJson, AuditTrailClient.RecordAuditFactRequest.class);
        } catch (Exception e) {
            // An unparseable payload will never become parseable. Reporting success drops it from
            // the queue on purpose: retrying forever would hide a corrupt row behind a counter
            // that only grows.
            log.error(
                    "Outbox payload is not a valid RecordAuditFactRequest, dropping: {}",
                    e.getMessage());
            return true;
        }
        try {
            client.recordFact(request);
            return true;
        } catch (Exception e) {
            log.warn("Outbox redelivery to hist-service failed: {}", e.getMessage());
            return false;
        }
    }
}
