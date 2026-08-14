package com.neuroassist.gest.infrastructure.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Observability configuration for gest-service (GEST-021).
 *
 * <p>Registers common tags applied to all Micrometer metrics published by this service:
 *
 * <ul>
 *   <li>{@code service=gest-service} — identifies the microservice in dashboards
 *   <li>{@code layer=domain} — identifies the architectural layer producing business metrics
 * </ul>
 *
 * <p>Domain-level counters pre-registered at startup (ensures they appear in {@code
 * /actuator/prometheus} even before the first business operation):
 *
 * <ul>
 *   <li>{@code gest_tipo_gesto_registrations_total} — driven by TipoGestoService.register()
 *   <li>{@code gest_tipo_gesto_transitions_total} — driven by TipoGestoService.transition()
 *   <li>{@code gest_profile_versions_published_total} — driven by DetectionProfileService.publish()
 * </ul>
 *
 * <p>RULE-01 compliance: MeterRegistry is {@code io.micrometer}, not {@code org.springframework} —
 * domain.* is never touched; injection occurs only in application.service and
 * infrastructure.config.
 */
@Configuration
public class ObservabilityConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> gestMetricsCustomizer() {
        return registry ->
                registry.config().commonTags("service", "gest-service", "layer", "domain");
    }

    /**
     * Pre-registers the tipo-gesto registrations counter at startup.
     *
     * <p>Ensures the metric appears in {@code /actuator/prometheus} (at count=0) before the first
     * call to {@link com.neuroassist.gest.application.service.TipoGestoService#register}.
     */
    @Bean
    public Counter gestTipoGestoRegistrationsCounter(MeterRegistry registry) {
        return Counter.builder("gest_tipo_gesto_registrations_total")
                .description("Total number of TipoGesto primitives registered in gest-service")
                .tag("tenant", "startup")
                .register(registry);
    }

    /**
     * Pre-registers the tipo-gesto transitions counter at startup.
     *
     * <p>Ensures the metric appears in {@code /actuator/prometheus} before the first transition.
     */
    @Bean
    public Counter gestTipoGestoTransitionsCounter(MeterRegistry registry) {
        return Counter.builder("gest_tipo_gesto_transitions_total")
                .description(
                        "Total number of TipoGesto detectability-state transitions in gest-service")
                .tag("tenant", "startup")
                .tag("from_state", "REGISTERED")
                .tag("to_state", "PENDING_TRAINING")
                .register(registry);
    }

    /**
     * Pre-registers the profile versions published counter at startup.
     *
     * <p>Ensures the metric appears in {@code /actuator/prometheus} before the first publish.
     */
    @Bean
    public Counter gestProfileVersionsPublishedCounter(MeterRegistry registry) {
        return Counter.builder("gest_profile_versions_published_total")
                .description("Total number of DetectionProfile versions published by gest-service")
                .tag("tenant", "startup")
                .register(registry);
    }
}
