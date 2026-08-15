package com.neuroassist.evnt.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers EVNT business metrics in the Micrometer registry.
 *
 * <p>Counters are pre-registered here so they appear in Prometheus export with the correct
 * description and tags even before the first event occurs (avoids "missing metric" gaps in
 * dashboards). Each counter can be injected into services via {@code @Bean}.
 */
@Configuration
public class EvntMeterConfig {

    /**
     * Counter incremented whenever all retry attempts for a notification dispatch are exhausted.
     * Observers on this counter must trigger an operator alert (no caregiver was notified).
     *
     * <p>Metric name: {@code evnt.dispatch.exhausted} Tags: {@code module=evnt}
     */
    @Bean
    public Counter dispatchExhaustedCounter(MeterRegistry registry) {
        return Counter.builder("evnt.dispatch.exhausted")
                .description(
                        "Number of notification dispatches that exhausted all retry attempts"
                                + " without successful delivery.")
                .tag("module", "evnt")
                .register(registry);
    }
}
