package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.PanelStreamUseCase;
import com.neuroassist.dash.application.port.out.NotificationDispatchBackPort;
import com.neuroassist.dash.infrastructure.sse.PanelSessionRegistry;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Application service for SSE panel session management (DASH-004). No @Transactional: DASH has no
 * database (ADR-004). No TenantContextHolder.require(): DASH is a BFF; tenant comes from JWT, not
 * empresa scope.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PanelStreamService implements PanelStreamUseCase {

    private final PanelSessionRegistry panelSessionRegistry;
    private final NotificationDispatchBackPort notificationDispatchBackPort;

    @Override
    public SseEmitter openSession(String caregiverId, String tenantId) {
        log.info("Opening SSE session: caregiver={} tenant={}", caregiverId, tenantId);
        return panelSessionRegistry.open(caregiverId, tenantId);
    }

    @Override
    public void heartbeat(String caregiverId) {
        panelSessionRegistry
                .get(caregiverId)
                .ifPresentOrElse(
                        emitter -> {
                            try {
                                emitter.send(
                                        SseEmitter.event()
                                                .name("heartbeat")
                                                .data(Map.of("type", "heartbeat")));
                                log.debug("Heartbeat sent to caregiver={}", caregiverId);
                            } catch (IOException e) {
                                log.warn(
                                        "Failed to send heartbeat to caregiver={}: {}",
                                        caregiverId,
                                        e.getMessage());
                                panelSessionRegistry.close(caregiverId);
                            }
                        },
                        () ->
                                log.debug(
                                        "Heartbeat skipped — no active session for caregiver={}",
                                        caregiverId));
    }

    @Override
    public void closeSession(String caregiverId) {
        log.info("Closing SSE session: caregiver={}", caregiverId);
        panelSessionRegistry.close(caregiverId);
    }

    @Override
    public SseEmitter replayCatchup(String caregiverId, String tenantId) {
        log.info("Replay catch-up: caregiver={} tenant={}", caregiverId, tenantId);
        SseEmitter emitter = panelSessionRegistry.open(caregiverId, tenantId);
        try {
            emitter.send(
                    SseEmitter.event()
                            .name("catchup-complete")
                            .data(Map.of("type", "catchup-complete")));
        } catch (IOException e) {
            log.warn(
                    "Failed to send catchup-complete to caregiver={}: {}",
                    caregiverId,
                    e.getMessage());
            panelSessionRegistry.close(caregiverId);
        }
        return emitter;
    }
}
