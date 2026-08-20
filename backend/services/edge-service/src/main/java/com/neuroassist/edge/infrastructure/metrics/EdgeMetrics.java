package com.neuroassist.edge.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * EdgeMetrics — EDGE-017 (P7).
 *
 * <p>Centralizes Micrometer counter registration and increment for business-relevant edge
 * operations. Placed in {@code infrastructure.metrics} per hexagonal architecture — metrics
 * instrumentation is an infrastructure cross-cutting concern, not a domain or application concern.
 *
 * <p>Counters exposed:
 *
 * <ul>
 *   <li>{@code edge.heartbeats.received} (tags: tenantId, status) — incremented per heartbeat
 *   <li>{@code edge.pairing.transitions} (tags: fromState, toState) — incremented on
 *       pair/unpair/retire
 *   <li>{@code edge.profile.drift.detected} — incremented when running agent version != GEST
 *       version
 * </ul>
 *
 * <p>Uses {@code Counter.builder(...).register(meterRegistry)} pattern (lazy registration) so
 * counters are only created when first used, avoiding zero-value noise in dashboards.
 */
@Component
@RequiredArgsConstructor
public class EdgeMetrics {

    private static final String HEARTBEATS_RECEIVED = "edge.heartbeats.received";
    private static final String PAIRING_TRANSITIONS = "edge.pairing.transitions";
    private static final String PROFILE_DRIFT_DETECTED = "edge.profile.drift.detected";

    private final MeterRegistry meterRegistry;

    /**
     * Increment heartbeat counter with tenant and derived liveness status tags.
     *
     * @param tenantId current tenant identifier (from TenantContextHolder)
     * @param status derived liveness status after heartbeat (e.g., "online", "offline")
     */
    public void recordHeartbeat(String tenantId, String status) {
        Counter.builder(HEARTBEATS_RECEIVED)
                .tag("tenantId", tenantId != null ? tenantId : "unknown")
                .tag("status", status != null ? status : "unknown")
                .description("Total heartbeats received per tenant and liveness status")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increment pairing FSM transition counter.
     *
     * @param fromState previous PairingState name (e.g., "registered", "paired")
     * @param toState target PairingState name (e.g., "paired", "unpaired", "retired")
     */
    public void recordPairingTransition(String fromState, String toState) {
        Counter.builder(PAIRING_TRANSITIONS)
                .tag("fromState", fromState != null ? fromState : "unknown")
                .tag("toState", toState != null ? toState : "unknown")
                .description("Total pairing FSM transitions (pair / unpair / retire)")
                .register(meterRegistry)
                .increment();
    }

    /**
     * Increment profile drift counter when the agent's running version does not match the current
     * GEST profile version (detected during heartbeat processing).
     */
    public void recordProfileDrift() {
        Counter.builder(PROFILE_DRIFT_DETECTED)
                .description(
                        "Total profile drift events detected (agent version != GEST current version)")
                .register(meterRegistry)
                .increment();
    }
}
