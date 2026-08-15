package com.neuroassist.evnt.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.neuroassist.evnt.domain.model.Evento;
import com.neuroassist.evnt.infrastructure.security.TenantContextHolder;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Unit tests for SseEmitterAdapter (EVNT-013 / EVNT-014). Uses mock/spy SseEmitter instances to
 * verify fan-out, cleanup, backfill, keepalive, and cross-tenant isolation.
 */
class SseEmitterAdapterTest {

    private SseEmitterAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new SseEmitterAdapter();
        // Inject default timeout (would normally come from @Value)
        // We can't use @Value in plain unit tests, so we rely on default from @Scheduled config.
        // The emitter timeout defaults to 0 (no timeout) when not set via @Value in plain tests.
        // We create SseEmitter manually in tests to control behavior.
    }

    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // registerEmitter
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("registerEmitter adds emitter to registry for the given tenant")
    void registerEmitter_addsToRegistry() {
        SseEmitter emitter = adapter.registerEmitter("tenant_a");

        assertThat(emitter).isNotNull();
        assertThat(adapter.getRegistry()).containsKey("tenant_a");
        assertThat(adapter.getRegistry().get("tenant_a")).hasSize(1);
    }

    @Test
    @DisplayName("registerEmitter allows multiple emitters per tenant")
    void registerEmitter_multipleEmittersPerTenant() {
        adapter.registerEmitter("tenant_a");
        adapter.registerEmitter("tenant_a");

        assertThat(adapter.getRegistry().get("tenant_a")).hasSize(2);
    }

    @Test
    @DisplayName("onCompletion callback removes emitter from registry")
    void registerEmitter_onCompletion_removesFromRegistry() {
        SseEmitter emitter = adapter.registerEmitter("tenant_a");
        assertThat(adapter.getRegistry().get("tenant_a")).hasSize(1);

        // SseEmitter.complete() without an HTTP handler does not fire onCompletion callbacks
        // (Spring ResponseBodyEmitter requires a bound Handler to propagate lifecycle events).
        // Directly invoke removeEmitter — the same Runnable registered via emitter.onCompletion().
        adapter.removeEmitter("tenant_a", emitter);

        assertThat(adapter.getRegistry().containsKey("tenant_a")).isFalse();
    }

    // -------------------------------------------------------------------------
    // broadcast — fan-out and cross-tenant isolation
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("broadcast sends frame to all emitters of the tenant in context")
    void broadcast_sendsFrameToCorrectTenant() throws IOException {
        SseEmitter emitterA1 = spy(new SseEmitter(0L));
        SseEmitter emitterA2 = spy(new SseEmitter(0L));
        SseEmitter emitterB = spy(new SseEmitter(0L));

        adapter.getRegistry()
                .computeIfAbsent("tenant_a", k -> new CopyOnWriteArrayList<>())
                .addAll(List.of(emitterA1, emitterA2));
        adapter.getRegistry()
                .computeIfAbsent("tenant_b", k -> new CopyOnWriteArrayList<>())
                .add(emitterB);

        TenantContextHolder.set("tenant_a");
        Evento evento = buildEvento("emergencia", 42L);

        adapter.broadcast(evento);

        verify(emitterA1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitterA2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitterB, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("broadcast skips when no tenant in ThreadLocal")
    void broadcast_skipsWhenNoTenant() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(0L));
        adapter.getRegistry()
                .computeIfAbsent("tenant_a", k -> new CopyOnWriteArrayList<>())
                .add(emitter);

        // TenantContextHolder NOT set
        adapter.broadcast(buildEvento("emergencia", 1L));

        verify(emitter, never()).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("broadcast removes dead emitter when send throws IOException")
    void broadcast_removesDeadEmitterOnIOException() throws IOException {
        SseEmitter dead = spy(new SseEmitter(0L));
        doThrow(new IOException("connection reset"))
                .when(dead)
                .send(any(SseEmitter.SseEventBuilder.class));

        adapter.getRegistry()
                .computeIfAbsent("tenant_a", k -> new CopyOnWriteArrayList<>())
                .add(dead);

        TenantContextHolder.set("tenant_a");
        adapter.broadcast(buildEvento("emergencia", 1L));

        // Dead emitter pruned
        assertThat(adapter.getRegistry().get("tenant_a")).doesNotContain(dead);
    }

    // -------------------------------------------------------------------------
    // sendBackfill
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("sendBackfill sends replay frames for all missed eventos")
    void sendBackfill_replaysAllMissedEventos() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(0L));
        List<Evento> missed = List.of(buildEvento("emergencia", 51L), buildEvento("rotina", 52L));

        adapter.sendBackfill(emitter, missed);

        verify(emitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("sendBackfill stops and completes emitter with error on IOException")
    void sendBackfill_stopsOnIOException() throws IOException {
        SseEmitter emitter = spy(new SseEmitter(0L));
        doThrow(new IOException("broken pipe"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        List<Evento> missed = List.of(buildEvento("emergencia", 51L), buildEvento("rotina", 52L));

        adapter.sendBackfill(emitter, missed);

        // completeWithError called, second evento NOT attempted
        verify(emitter, times(1)).completeWithError(any(IOException.class));
        verify(emitter, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    // -------------------------------------------------------------------------
    // keepAlive
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("keepAlive sends keepalive frame to every active emitter")
    void keepAlive_sendsToAllActiveEmitters() throws IOException {
        SseEmitter e1 = spy(new SseEmitter(0L));
        SseEmitter e2 = spy(new SseEmitter(0L));
        adapter.getRegistry()
                .computeIfAbsent("tenant_a", k -> new CopyOnWriteArrayList<>())
                .addAll(List.of(e1, e2));

        adapter.keepAlive();

        verify(e1, times(1)).send(any(SseEmitter.SseEventBuilder.class));
        verify(e2, times(1)).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("keepAlive prunes dead emitters that throw IOException")
    void keepAlive_prunesDeadEmitters() throws IOException {
        SseEmitter live = spy(new SseEmitter(0L));
        SseEmitter dead = spy(new SseEmitter(0L));
        doThrow(new IOException("closed")).when(dead).send(any(SseEmitter.SseEventBuilder.class));

        CopyOnWriteArrayList<SseEmitter> list = new CopyOnWriteArrayList<>(List.of(live, dead));
        adapter.getRegistry().put("tenant_a", list);

        adapter.keepAlive();

        assertThat(adapter.getRegistry().get("tenant_a")).containsOnly(live).doesNotContain(dead);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Evento buildEvento(String urgencia, Long seq) {
        return new Evento(
                UUID.randomUUID(),
                "pac-1",
                "cmd-ref",
                "command label",
                urgencia,
                "NAO_AVALIADO",
                null,
                null,
                Instant.now(),
                Instant.now(),
                seq);
    }
}
