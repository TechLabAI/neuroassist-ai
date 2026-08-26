package com.neuroassist.gest.application.port.out;

/**
 * Outbound port for business-level observability in gest-service.
 *
 * <p>Keeps application.service decoupled from Micrometer (RULE-04). The infrastructure adapter
 * ({@code MicrometerObservabilityAdapter}) implements this interface and owns the MeterRegistry
 * dependency and TenantContextHolder access.
 */
public interface GestObservabilityPort {

    /**
     * Increments the counter tracking TipoGesto primitive registrations.
     *
     * <p>Adapter resolves tenant context internally via TenantContextHolder.
     */
    void incrementTipoGestoRegistrations();

    /**
     * Increments the counter tracking TipoGesto detectability-state transitions.
     *
     * @param fromState origin state name
     * @param toState target state name
     *     <p>Adapter resolves tenant context internally via TenantContextHolder.
     */
    void incrementTipoGestoTransitions(String fromState, String toState);

    /**
     * Increments the counter tracking DetectionProfile version publications.
     *
     * <p>Adapter resolves tenant context internally via TenantContextHolder.
     */
    void incrementProfileVersionsPublished();
}
