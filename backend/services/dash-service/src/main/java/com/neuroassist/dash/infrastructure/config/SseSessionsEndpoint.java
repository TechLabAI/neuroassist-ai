package com.neuroassist.dash.infrastructure.config;

import com.neuroassist.dash.infrastructure.sse.PanelSessionRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Custom Actuator endpoint: GET /actuator/sessions
 * Returns the number of active SSE sessions for operational monitoring (DASH-023).
 */
@Component
@Endpoint(id = "sessions")
@RequiredArgsConstructor
public class SseSessionsEndpoint {

    private final PanelSessionRegistry registry;

    @ReadOperation
    public Map<String, Object> sessions() {
        return Map.of("activeSessions", registry.activeSessionCount());
    }
}
