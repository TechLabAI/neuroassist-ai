package com.neuroassist.acs.infrastructure.config;

import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers custom Micrometer metrics for the ACS service (ACS-025).
 *
 * <p>Metrics registered:
 *
 * <ul>
 *   <li>{@code acs.auth.attempts} — Counter, tag {@code status=success|failure}
 *   <li>{@code acs.tenant.provisioning.duration} — Timer, tag {@code status=success|failure}
 *   <li>{@code acs.caregiver.active} — Gauge (current-tenant active caregivers)
 * </ul>
 *
 * <p>RULE-09: base package {@code com.neuroassist.acs}. DI: {@link RequiredArgsConstructor} (no
 * {@code @Autowired} in field).
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class ObservabilityConfig {

    private final CuidadorRepositoryPort cuidadorRepository;

    /** Counter for successful authentication attempts. Tag: {@code status=success} */
    @Bean
    public Counter authSuccessCounter(MeterRegistry registry) {
        return Counter.builder("acs.auth.attempts")
                .tag("status", "success")
                .description("Number of successful ACS authentication attempts")
                .register(registry);
    }

    /** Counter for failed authentication attempts. Tag: {@code status=failure} */
    @Bean
    public Counter authFailureCounter(MeterRegistry registry) {
        return Counter.builder("acs.auth.attempts")
                .tag("status", "failure")
                .description("Number of failed ACS authentication attempts")
                .register(registry);
    }

    /**
     * Timer for tenant provisioning duration. Tag: {@code status=success|failure} added at call
     * site.
     */
    @Bean
    public Timer tenantProvisioningTimer(MeterRegistry registry) {
        return Timer.builder("acs.tenant.provisioning.duration")
                .description("Duration of ACS tenant provisioning operations")
                .register(registry);
    }

    /**
     * Gauge for the number of active caregivers in the current tenant context.
     *
     * <p>Called at Prometheus scrape time; returns 0 if no tenant context is set (e.g., during
     * health checks or when no request is in progress).
     */
    @Bean
    public Gauge caregiverActiveGauge(MeterRegistry registry) {
        return Gauge.builder(
                        "acs.caregiver.active",
                        () -> {
                            try {
                                return (double) cuidadorRepository.countActive();
                            } catch (Exception e) {
                                log.debug(
                                        "acs.caregiver.active gauge: no tenant context or error — returning 0: {}",
                                        e.getMessage());
                                return 0.0;
                            }
                        })
                .description("Number of active caregivers in the current tenant context")
                .register(registry);
    }
}
