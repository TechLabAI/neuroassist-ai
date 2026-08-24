package com.neuroassist.evnt.infrastructure.sse;

import com.neuroassist.evnt.application.port.out.SseEventBroadcastPort;
import com.neuroassist.evnt.application.port.out.SseRegistrationPort;
import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.domain.model.Tier;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Real SSE fan-out adapter (EVNT-013). Implements: - Per-tenant ConcurrentHashMap registry with
 * CopyOnWriteArrayList of SseEmitter instances. - broadcast() picks tenant from TenantContextHolder
 * and fans-out to all registered emitters. - Dead emitters are removed on IOException or
 * IllegalStateException during send. - keepAlive() runs on a fixed delay, sending a "keepalive"
 * frame to all open emitters. - sendBackfill() replays Last-Event-ID frames synchronously on
 * reconnect (EVNT-014).
 *
 * <p>RULE-04/05 compliance: api.controller depends on SseRegistrationPort (application.port.out),
 * not on this class directly.
 */
@Slf4j
@Component
public class SseEmitterAdapter implements SseEventBroadcastPort, SseRegistrationPort {

    @Value("${evnt.sse.emitter-timeout-ms:180000}")
    private long emitterTimeoutMs;

    /**
     * Registry: tenantId → list of active SseEmitters. CopyOnWriteArrayList ensures safe iteration
     * while concurrent adds/removes happen.
     */
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> registry =
            new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // SseRegistrationPort
    // -------------------------------------------------------------------------

    @Override
    public SseEmitter registerEmitter(String tenantId) {
        SseEmitter emitter = new SseEmitter(emitterTimeoutMs);

        registry.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(emitter);
        log.debug("SSE emitter registered tenantId={}", tenantId);

        Runnable cleanup = () -> removeEmitter(tenantId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(
                () -> {
                    emitter.complete();
                    cleanup.run();
                });
        emitter.onError(e -> cleanup.run());

        return emitter;
    }

    @Override
    public void sendBackfill(SseEmitter emitter, List<Evento> eventos) {
        for (Evento evento : eventos) {
            try {
                SseEmitter.SseEventBuilder event =
                        SseEmitter.event()
                                .name("evento.created")
                                .data(buildEventData(evento), MediaType.APPLICATION_JSON);
                if (evento.seq() != null) {
                    event.id(String.valueOf(evento.seq()));
                }
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                log.warn(
                        "Backfill send failed — closing emitter seq={} error={}",
                        evento.seq(),
                        e.getMessage());
                emitter.completeWithError(e);
                return;
            }
        }
        log.debug("Backfill sent events={}", eventos.size());
    }

    // -------------------------------------------------------------------------
    // SseEventBroadcastPort
    // -------------------------------------------------------------------------

    /**
     * Broadcasts an Evento to all emitters registered for the current tenant. Tenant is read from
     * TenantContextHolder (set by TenantContextFilter on the request thread).
     */
    @Override
    public void broadcast(Evento evento) {
        String tenantId = TenantContextHolder.getTenantIdOrNull();
        if (tenantId == null) {
            log.warn("SSE broadcast skipped — no tenant in ThreadLocal eventoId={}", evento.id());
            return;
        }
        broadcastToTenant(tenantId, "evento.created", evento);
    }

    /**
     * Broadcasts an Evento update (e.g. confirmation) to the specified tenant's emitters. Called
     * explicitly by services that know the tenant at confirmation time.
     */
    public void broadcastUpdate(String tenantId, Evento evento) {
        broadcastToTenant(tenantId, "evento.updated", evento);
    }

    // -------------------------------------------------------------------------
    // Keepalive scheduler (EVNT-014)
    // -------------------------------------------------------------------------

    /**
     * Sends a keepalive comment frame to every open emitter. Dead emitters are pruned in-place.
     * Runs every evnt.sse.keepalive-seconds (default 15 s).
     */
    @Scheduled(fixedDelayString = "${evnt.sse.keepalive-seconds:15}000")
    public void keepAlive() {
        if (registry.isEmpty()) {
            return;
        }
        registry.forEach(
                (tenantId, emitters) -> {
                    List<SseEmitter> dead = new ArrayList<>();
                    for (SseEmitter emitter : emitters) {
                        try {
                            emitter.send(SseEmitter.event().name("keepalive").comment("ping"));
                        } catch (IOException | IllegalStateException e) {
                            dead.add(emitter);
                        }
                    }
                    if (!dead.isEmpty()) {
                        emitters.removeAll(dead);
                        log.debug(
                                "Keepalive pruned dead emitters tenantId={} count={}",
                                tenantId,
                                dead.size());
                    }
                });
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private void broadcastToTenant(String tenantId, String eventType, Evento evento) {
        CopyOnWriteArrayList<SseEmitter> emitters = registry.get(tenantId);
        if (emitters == null || emitters.isEmpty()) {
            log.debug("No SSE emitters for tenantId={} — skipping broadcast", tenantId);
            return;
        }

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                SseEmitter.SseEventBuilder event =
                        SseEmitter.event()
                                .name(eventType)
                                .data(buildEventData(evento), MediaType.APPLICATION_JSON);
                if (evento.seq() != null) {
                    event.id(String.valueOf(evento.seq()));
                }
                emitter.send(event);
            } catch (IOException | IllegalStateException e) {
                log.debug("Dead emitter detected during broadcast tenantId={}", tenantId);
                dead.add(emitter);
            }
        }
        if (!dead.isEmpty()) {
            emitters.removeAll(dead);
        }
        log.debug(
                "SSE broadcast eventType={} tenantId={} emitters={}",
                eventType,
                tenantId,
                emitters.size());
    }

    void removeEmitter(String tenantId, SseEmitter emitter) {
        registry.computeIfPresent(
                tenantId,
                (k, list) -> {
                    list.remove(emitter);
                    return list.isEmpty() ? null : list;
                });
        log.debug("SSE emitter removed tenantId={}", tenantId);
    }

    private Map<String, Object> buildEventData(Evento evento) {
        return Map.of(
                "eventoId", evento.id().toString(),
                "urgencia", evento.urgenciaSnapshot(),
                "tier", deriveTier(evento.urgenciaSnapshot()),
                "seq", evento.seq() != null ? evento.seq() : 0L);
    }

    private String deriveTier(String urgencia) {
        return "emergencia".equalsIgnoreCase(urgencia)
                ? Tier.full_screen_alarm.name()
                : Tier.discreet.name();
    }

    /** Package-private accessor for unit tests (verifies registry state). */
    ConcurrentHashMap<String, CopyOnWriteArrayList<SseEmitter>> getRegistry() {
        return registry;
    }
}
