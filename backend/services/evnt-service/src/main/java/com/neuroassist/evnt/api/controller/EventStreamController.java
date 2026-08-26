package com.neuroassist.evnt.api.controller;

import com.neuroassist.evnt.application.port.in.TenantResolverPort;
import com.neuroassist.evnt.application.port.out.EventoRepositoryPort;
import com.neuroassist.evnt.application.port.out.SseRegistrationPort;
import com.neuroassist.evnt.domain.model.Evento;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE live-delivery endpoint (EVNT-013 / EVNT-014).
 *
 * <p>GET /api/v1/events/stream → opens an SSE connection for the authenticated caregiver's tenant.
 * Supports Last-Event-ID reconnect: sends backfill frames for all eventos with seq > lastEventId
 * before handing the stream to SseEmitterAdapter for live delivery.
 *
 * <p>Depends on TenantResolverPort, SseRegistrationPort and EventoRepositoryPort (all in
 * application.port) to preserve RULE-04 (api.* must not depend on infrastructure.*).
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
public class EventStreamController {

    private final TenantResolverPort tenantResolverPort;
    private final SseRegistrationPort sseRegistrationPort;
    private final EventoRepositoryPort eventoRepositoryPort;

    /**
     * Opens an SSE stream for the tenant derived from the current JWT / TenantContextFilter.
     *
     * @param lastEventId optional Last-Event-ID header — if present, eventos with seq &gt; value
     *     are replayed as backfill frames before live delivery resumes.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasAuthority('evnt.event-query.read')")
    public SseEmitter streamEvents(
            @RequestHeader(value = "Last-Event-ID", required = false) Long lastEventId) {

        String tenantId = tenantResolverPort.getTenantIdRequired();
        log.info("SSE connection opened tenantId={} lastEventId={}", tenantId, lastEventId);

        SseEmitter emitter = sseRegistrationPort.registerEmitter(tenantId);

        if (lastEventId != null) {
            List<Evento> missed = eventoRepositoryPort.findBySeqGreaterThan(lastEventId);
            if (!missed.isEmpty()) {
                log.info(
                        "SSE backfill tenantId={} lastEventId={} count={}",
                        tenantId,
                        lastEventId,
                        missed.size());
                sseRegistrationPort.sendBackfill(emitter, missed);
            }
        }

        return emitter;
    }
}
