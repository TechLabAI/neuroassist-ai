package com.neuroassist.evnt.infrastructure.clients.adapter;

import com.neuroassist.evnt.application.port.out.GestValidationPort;
import com.neuroassist.evnt.infrastructure.clients.GestValidationClient;
import feign.FeignException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Checks every primitive carried by a report against gest-service's catalogue.
 *
 * <p><b>Fail-closed, and deliberately without a circuit-breaker fallback.</b> A gesture whose
 * primitives GEST does not recognise must not become an alarm. Wrapping this call in a fallback
 * that returns {@code true} on failure would turn an unreachable GEST into a stream of unvalidated
 * alarms; wrapping it in one that returns {@code false} would turn an outage into "the patient's
 * gesture is invalid". Both are worse than a failed request, which the caller surfaces honestly.
 *
 * <p>This is the same lesson as C-19 read from the other side: there, a {@code @CircuitBreaker}
 * without {@code ignoreExceptions} laundered a 403 into "service degraded", and the heartbeat
 * reported success while doing nothing.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GestValidationFeignAdapter implements GestValidationPort {

    private final GestValidationClient client;

    @Override
    public boolean validate(GestValidationPort.GestValidationRequest request) {
        if (request.tipoGestoIds() == null || request.tipoGestoIds().isEmpty()) {
            log.warn("Report carries no gesture primitives — rejecting");
            return false;
        }
        for (String tipoGestoId : request.tipoGestoIds()) {
            if (!isKnownToGest(tipoGestoId)) {
                return false;
            }
        }
        return true;
    }

    private boolean isKnownToGest(String tipoGestoId) {
        UUID id;
        try {
            id = UUID.fromString(tipoGestoId);
        } catch (IllegalArgumentException e) {
            log.warn("Report carries a primitive id that is not a UUID: {}", tipoGestoId);
            return false;
        }
        try {
            client.findPrimitive(id);
            return true;
        } catch (FeignException.NotFound e) {
            // 404 is the answer, not a failure: gest-service does not know this primitive.
            log.warn("Primitive {} is unknown to gest-service", id);
            return false;
        }
    }
}
