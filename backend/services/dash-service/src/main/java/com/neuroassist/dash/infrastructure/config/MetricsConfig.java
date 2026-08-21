package com.neuroassist.dash.infrastructure.config;

import com.neuroassist.dash.infrastructure.sse.PanelSessionRegistry;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Micrometer metrics configuration for DASH observability (DASH-023). Exposes a gauge tracking
 * active SSE sessions in PanelSessionRegistry.
 */
@Configuration
@RequiredArgsConstructor
public class MetricsConfig {

    private final PanelSessionRegistry panelSessionRegistry;

    /**
     * Gauge: dashboard.sse.sessions.active Increments/decrements with open SSE sessions in
     * PanelSessionRegistry.
     */
    @Bean
    public MeterBinder activeSseSessionsGauge() {
        return registry ->
                Gauge.builder(
                                "dashboard.sse.sessions.active",
                                panelSessionRegistry,
                                PanelSessionRegistry::activeSessionCount)
                        .description("Number of active SSE sessions in the DASH panel")
                        .register(registry);
    }
}
