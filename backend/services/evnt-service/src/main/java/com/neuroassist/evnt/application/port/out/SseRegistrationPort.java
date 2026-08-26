package com.neuroassist.evnt.application.port.out;

import com.neuroassist.evnt.domain.model.Evento;
import java.util.List;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Outbound port: SSE emitter registration and backfill delivery (EVNT-013 / EVNT-014).
 *
 * <p>Separating registration from broadcast allows EventStreamController to depend on this port
 * (application layer) without directly referencing infrastructure.sse, keeping RULE-04 intact.
 */
public interface SseRegistrationPort {

    /**
     * Registers a new SseEmitter for the given tenant, wires completion/timeout/error cleanup
     * callbacks, and returns the emitter to be returned as the controller response.
     */
    SseEmitter registerEmitter(String tenantId);

    /**
     * Sends backfill frames from {@code eventos} to the given emitter immediately (synchronous).
     * Intended for Last-Event-ID replay on reconnect.
     */
    void sendBackfill(SseEmitter emitter, List<Evento> eventos);
}
