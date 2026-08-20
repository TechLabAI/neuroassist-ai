package com.neuroassist.hist.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Micrometer meters for hist-service observability (P7, HIST-017).
 *
 * <p>Exported meters:
 *
 * <ul>
 *   <li>{@code hist.audit.fact.recorded.total} — tags: tenant, factType
 *   <li>{@code hist.duplicate.fact.ignored.total} — tags: tenant, factType
 *   <li>{@code hist.chain.verification.total} — tags: tenant, result (ok|broken|empty)
 *   <li>{@code hist.ingestion.latency.seconds} — Timer, tags: tenant
 *   <li>{@code hist.acs.scope.resolution.total} — tags: tenant, result (ok|unavailable)
 * </ul>
 *
 * <p>Never adds any {@code payload} field to MDC or metric tags (LGPD compliance).
 */
@Component
@RequiredArgsConstructor
public class HistMetrics {

    private final MeterRegistry registry;

    /** Increment when an audit fact is successfully appended to the ledger. */
    public void incrementAuditFactRecorded(String tenant, String factType) {
        Counter.builder("hist.audit.fact.recorded.total")
                .tag("tenant", tenant)
                .tag("factType", factType)
                .description("Total audit facts recorded in the ledger")
                .register(registry)
                .increment();
    }

    /** Increment when a duplicate factId is detected and the original record is returned. */
    public void incrementDuplicateFactIgnored(String tenant, String factType) {
        Counter.builder("hist.duplicate.fact.ignored.total")
                .tag("tenant", tenant)
                .tag("factType", factType)
                .description("Total duplicate facts ignored via idempotency check")
                .register(registry)
                .increment();
    }

    /**
     * Increment for each chain verification result.
     *
     * @param result one of {@code "ok"} (intact), {@code "broken"}, or {@code "empty"}
     */
    public void incrementChainVerificationTotal(String tenant, String result) {
        Counter.builder("hist.chain.verification.total")
                .tag("tenant", tenant)
                .tag("result", result)
                .description("Total chain verification runs by result")
                .register(registry)
                .increment();
    }

    /**
     * Increment for each ACS scope resolution call.
     *
     * @param result one of {@code "ok"} or {@code "unavailable"}
     */
    public void incrementAcsScopeResolutionTotal(String tenant, String result) {
        Counter.builder("hist.acs.scope.resolution.total")
                .tag("tenant", tenant)
                .tag("result", result)
                .description("Total ACS actor scope resolution calls by result")
                .register(registry)
                .increment();
    }

    /**
     * Returns the ingestion latency Timer for the given tenant. Use {@link Timer#record} or {@link
     * io.micrometer.core.instrument.Timer.Sample#stop} to record durations.
     */
    public Timer ingestionLatencyTimer(String tenant) {
        return Timer.builder("hist.ingestion.latency.seconds")
                .tag("tenant", tenant)
                .description("Audit fact ingestion end-to-end latency")
                .register(registry);
    }
}
