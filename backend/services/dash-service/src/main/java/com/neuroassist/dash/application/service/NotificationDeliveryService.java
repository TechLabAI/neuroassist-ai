package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.NotificationDeliveryUseCase;
import com.neuroassist.dash.application.port.out.NotificationDispatchBackPort;
import com.neuroassist.dash.domain.model.DeliveryResult;
import com.neuroassist.dash.domain.model.NotificationPayload;
import com.neuroassist.dash.infrastructure.sse.PanelSessionRegistry;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Application service for notification delivery via SSE (DASH-005/DASH-006). No @Transactional:
 * DASH has no database (ADR-004). No TenantContextHolder.require(): DASH is a BFF; tenant comes
 * from JWT. Idempotency guaranteed via in-memory ConcurrentHashSet of delivered dispatchIds.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryService implements NotificationDeliveryUseCase {

    private final PanelSessionRegistry panelSessionRegistry;
    private final NotificationDispatchBackPort notificationDispatchBackPort;

    /** In-memory idempotency guard (MVP). Survives until process restart. */
    private final Set<String> deliveredIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    @Override
    public DeliveryResult deliver(
            String caregiverId, NotificationPayload payload, String tenantId) {
        String dispatchId = payload.dispatchId();
        log.info(
                "Delivering notification: dispatchId={} caregiver={} tier={}",
                dispatchId,
                caregiverId,
                payload.tier());

        // Idempotency: skip redelivery of already-delivered notifications
        if (deliveredIds.contains(dispatchId)) {
            log.debug("Duplicate dispatch ignored (idempotent): dispatchId={}", dispatchId);
            return DeliveryResult.alreadyDelivered(dispatchId);
        }

        // No active SSE session — report and signal EVNT to retry
        if (!panelSessionRegistry.isOpen(caregiverId)) {
            log.info(
                    "No open SSE session for caregiver={} — dispatchId={}",
                    caregiverId,
                    dispatchId);
            notificationDispatchBackPort.reportNoSession(dispatchId);
            return DeliveryResult.noSession(dispatchId);
        }

        // Deliver via SSE
        SseEmitter emitter = panelSessionRegistry.get(caregiverId).orElse(null);
        if (emitter == null) {
            log.warn(
                    "Session was present but emitter is gone for caregiver={} — dispatchId={}",
                    caregiverId,
                    dispatchId);
            notificationDispatchBackPort.reportNoSession(dispatchId);
            return DeliveryResult.noSession(dispatchId);
        }

        try {
            emitter.send(SseEmitter.event().name("notification").data(payload));
        } catch (IOException e) {
            log.warn(
                    "SSE send failed for caregiver={} dispatchId={}: {}",
                    caregiverId,
                    dispatchId,
                    e.getMessage());
            panelSessionRegistry.close(caregiverId);
            notificationDispatchBackPort.reportNoSession(dispatchId);
            return DeliveryResult.noSession(dispatchId);
        }

        deliveredIds.add(dispatchId);
        notificationDispatchBackPort.reportDelivered(dispatchId);
        log.info("Notification delivered: dispatchId={} caregiver={}", dispatchId, caregiverId);
        return DeliveryResult.delivered(dispatchId);
    }
}
