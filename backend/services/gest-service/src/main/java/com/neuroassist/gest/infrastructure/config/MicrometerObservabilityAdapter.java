package com.neuroassist.gest.infrastructure.config;

import com.neuroassist.gest.application.port.out.GestObservabilityPort;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter that implements {@link GestObservabilityPort} using Micrometer.
 *
 * <p>Isolates {@code MeterRegistry} and {@code TenantContextHolder} inside the infrastructure layer
 * so that {@code application.service} classes never import infrastructure dependencies directly
 * (RULE-04 compliance).
 *
 * <p>Counter names and common tags mirror the pre-registration done in {@link ObservabilityConfig}
 * so that Micrometer merges increments against the same backing counter instance.
 */
@Component
@RequiredArgsConstructor
public class MicrometerObservabilityAdapter implements GestObservabilityPort {

    private final MeterRegistry meterRegistry;

    @Override
    public void incrementTipoGestoRegistrations() {
        String tenantId = TenantContextHolder.getTenantIdOrNull();
        meterRegistry
                .counter(
                        "gest_tipo_gesto_registrations_total",
                        "tenant",
                        tenantId != null ? tenantId : "unknown")
                .increment();
    }

    @Override
    public void incrementTipoGestoTransitions(String fromState, String toState) {
        String tenantId = TenantContextHolder.getTenantIdOrNull();
        meterRegistry
                .counter(
                        "gest_tipo_gesto_transitions_total",
                        "tenant",
                        tenantId != null ? tenantId : "unknown",
                        "from_state",
                        fromState,
                        "to_state",
                        toState)
                .increment();
    }

    @Override
    public void incrementProfileVersionsPublished() {
        String tenantId = TenantContextHolder.getTenantIdOrNull();
        meterRegistry
                .counter(
                        "gest_profile_versions_published_total",
                        "tenant",
                        tenantId != null ? tenantId : "unknown")
                .increment();
    }
}
