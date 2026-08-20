package com.neuroassist.dash.application.port.in;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Inbound port — manages SSE panel sessions for caregivers.
 * DASH P2: SSE Session + Dispatch Sink (DASH-004).
 */
public interface PanelStreamUseCase {

    /** Opens (or replaces) an SSE session for the given caregiver and returns the emitter. */
    SseEmitter openSession(String caregiverId, String tenantId);

    /** Sends a heartbeat event to the caregiver's active SSE session. */
    void heartbeat(String caregiverId);

    /** Closes the active SSE session for the given caregiver. */
    void closeSession(String caregiverId);

    /**
     * Replays unacknowledged notifications to the caregiver (catch-up on reconnect).
     * Opens a new session and emits a catchup-complete event.
     */
    SseEmitter replayCatchup(String caregiverId, String tenantId);
}
