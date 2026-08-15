package com.neuroassist.evnt.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.evnt.infrastructure.config.EvntMeterConfig;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for EVNT business metrics (P9 — EVNT-021 / EVNT-025).
 *
 * <p>Uses {@link SimpleMeterRegistry} (in-memory, no Prometheus export) so the test runs fast
 * without a Spring context. Each test creates its own registry to avoid counter state leakage.
 */
@DisplayName("EVNT Micrometer business metrics")
class EvntMetricsTest {

    private MeterRegistry registry;
    private EvntMeterConfig config;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        config = new EvntMeterConfig();
    }

    // -------------------------------------------------------------------------
    // evnt.dispatch.exhausted
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("evnt.dispatch.exhausted counter is registered in MeterRegistry")
    void dispatchExhaustedCounter_isRegistered() {
        Counter counter = config.dispatchExhaustedCounter(registry);

        assertThat(counter).isNotNull();
        Counter found = registry.find("evnt.dispatch.exhausted").counter();
        assertThat(found)
                .as("Counter 'evnt.dispatch.exhausted' must be present in the registry")
                .isNotNull();
    }

    @Test
    @DisplayName("evnt.dispatch.exhausted counter increments correctly")
    void dispatchExhaustedCounter_incrementsCorrectly() {
        Counter counter = config.dispatchExhaustedCounter(registry);

        assertThat(counter.count()).isEqualTo(0.0);

        counter.increment();
        assertThat(counter.count()).isEqualTo(1.0);

        counter.increment(3.0);
        assertThat(counter.count()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("evnt.dispatch.exhausted counter carries 'module=evnt' tag")
    void dispatchExhaustedCounter_hasModuleTag() {
        config.dispatchExhaustedCounter(registry);

        Counter found = registry.find("evnt.dispatch.exhausted").tag("module", "evnt").counter();
        assertThat(found)
                .as("Counter must carry tag module=evnt for Prometheus label cardinality")
                .isNotNull();
    }

    @Test
    @DisplayName("evnt.dispatch.exhausted counter has descriptive description")
    void dispatchExhaustedCounter_hasDescription() {
        Counter counter = config.dispatchExhaustedCounter(registry);

        assertThat(counter.getId().getDescription())
                .as("Counter must have a non-blank description for dashboard auto-discovery")
                .isNotBlank();
    }
}
