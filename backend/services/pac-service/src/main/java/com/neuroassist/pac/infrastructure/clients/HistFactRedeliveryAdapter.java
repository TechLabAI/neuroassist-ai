package com.neuroassist.pac.infrastructure.clients;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.pac.application.port.out.HistFactRedeliveryPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Redelivers a stored outbox payload to hist-service.
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

    private final HistFactClient histFactClient;
    private final ObjectMapper objectMapper;

    @Override
    public boolean redeliver(String payloadJson) {
        HistFactRequest request;
        try {
            request = objectMapper.readValue(payloadJson, HistFactRequest.class);
        } catch (Exception e) {
            // Unparseable payload will never become parseable — retrying is pointless.
            log.error(
                    "Outbox payload is not a valid HistFactRequest, dropping: {}", e.getMessage());
            return true;
        }
        try {
            histFactClient.recordFact(request);
            return true;
        } catch (Exception e) {
            log.warn("Outbox redelivery to hist-service failed: {}", e.getMessage());
            return false;
        }
    }
}
