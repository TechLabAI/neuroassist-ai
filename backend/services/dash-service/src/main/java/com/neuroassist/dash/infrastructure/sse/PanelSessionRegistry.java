package com.neuroassist.dash.infrastructure.sse;

import com.neuroassist.dash.domain.model.PanelSession;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * In-memory SSE session store (ADR-004: zero tables MVP). Key: caregiverId. Thread-safe via
 * ConcurrentHashMap.
 */
@Slf4j
@Component
public class PanelSessionRegistry {

    private final ConcurrentHashMap<String, SseRegistration> sessions = new ConcurrentHashMap<>();

    /**
     * Opens a new SSE emitter for the given caregiver. If a session already exists, completes the
     * old one before opening the new session.
     */
    public SseEmitter open(String caregiverId, String tenantId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        PanelSession session =
                new PanelSession(
                        caregiverId, tenantId, UUID.randomUUID().toString(), Instant.now());

        emitter.onCompletion(
                () -> {
                    log.debug("SSE emitter completed for caregiver={}", caregiverId);
                    close(caregiverId);
                });
        emitter.onTimeout(
                () -> {
                    log.debug("SSE emitter timed out for caregiver={}", caregiverId);
                    close(caregiverId);
                });
        emitter.onError(
                ex -> {
                    log.warn(
                            "SSE emitter error for caregiver={}: {}", caregiverId, ex.getMessage());
                    close(caregiverId);
                });

        SseRegistration existing = sessions.put(caregiverId, new SseRegistration(emitter, session));
        if (existing != null) {
            log.info("Replacing existing SSE session for caregiver={}", caregiverId);
            existing.emitter().complete();
        }

        log.info("SSE session opened: caregiver={} sessionId={}", caregiverId, session.sessionId());
        return emitter;
    }

    /** Returns the active emitter for the given caregiver, if present. */
    public Optional<SseEmitter> get(String caregiverId) {
        SseRegistration registration = sessions.get(caregiverId);
        return Optional.ofNullable(registration).map(SseRegistration::emitter);
    }

    /** Returns true if there is an open session for the given caregiver. */
    public boolean isOpen(String caregiverId) {
        return sessions.containsKey(caregiverId);
    }

    /** Closes and removes the session for the given caregiver. */
    public void close(String caregiverId) {
        SseRegistration registration = sessions.remove(caregiverId);
        if (registration != null) {
            log.info("SSE session closed: caregiver={}", caregiverId);
            registration.emitter().complete();
        }
    }

    /** Returns the number of active SSE sessions (for observability). */
    public int activeSessionCount() {
        return sessions.size();
    }
}
