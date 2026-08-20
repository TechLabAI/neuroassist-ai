package com.neuroassist.acs.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.neuroassist.acs.application.port.out.CuidadorRepositoryPort;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ObservabilityConfig}.
 *
 * <p>Verifies that all custom Micrometer meters are correctly registered in the {@link
 * MeterRegistry} with the expected names and tags (ACS-025).
 *
 * <p>Uses {@link SimpleMeterRegistry} — no Spring context required.
 */
class ObservabilityConfigTest {

    private MeterRegistry registry;
    private CuidadorRepositoryPort cuidadorRepository;
    private ObservabilityConfig config;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        cuidadorRepository = mock(CuidadorRepositoryPort.class);
        config = new ObservabilityConfig(cuidadorRepository);
    }

    @Test
    void authSuccessCounter_isRegisteredWithCorrectTags() {
        Counter counter = config.authSuccessCounter(registry);

        assertThat(counter).isNotNull();
        Counter found = registry.find("acs.auth.attempts").tags("status", "success").counter();
        assertThat(found).isNotNull();
    }

    @Test
    void authSuccessCounter_incrementsCorrectly() {
        Counter counter = config.authSuccessCounter(registry);
        counter.increment();
        counter.increment();

        assertThat(counter.count()).isEqualTo(2.0);
    }

    @Test
    void authFailureCounter_isRegisteredWithCorrectTags() {
        Counter counter = config.authFailureCounter(registry);

        assertThat(counter).isNotNull();
        Counter found = registry.find("acs.auth.attempts").tags("status", "failure").counter();
        assertThat(found).isNotNull();
    }

    @Test
    void tenantProvisioningTimer_isRegisteredWithCorrectName() {
        Timer timer = config.tenantProvisioningTimer(registry);

        assertThat(timer).isNotNull();
        Timer found = registry.find("acs.tenant.provisioning.duration").timer();
        assertThat(found).isNotNull();
    }

    @Test
    void caregiverActiveGauge_isRegisteredAndReflectsRepositoryValue() {
        when(cuidadorRepository.countActive()).thenReturn(7L);

        Gauge gauge = config.caregiverActiveGauge(registry);

        assertThat(gauge).isNotNull();
        Gauge found = registry.find("acs.caregiver.active").gauge();
        assertThat(found).isNotNull();
        assertThat(found.value()).isEqualTo(7.0);
    }

    @Test
    void caregiverActiveGauge_returnsZeroWhenRepositoryThrows() {
        when(cuidadorRepository.countActive()).thenThrow(new RuntimeException("No tenant context"));

        Gauge gauge = config.caregiverActiveGauge(registry);

        assertThat(gauge).isNotNull();
        // Gauge supplier swallows exception and returns 0.0
        assertThat(gauge.value()).isEqualTo(0.0);
    }

    @Test
    void allThreeMetricNames_areRegistered() {
        config.authSuccessCounter(registry);
        config.authFailureCounter(registry);
        config.tenantProvisioningTimer(registry);
        config.caregiverActiveGauge(registry);

        assertThat(registry.find("acs.auth.attempts").counters()).hasSize(2);
        assertThat(registry.find("acs.tenant.provisioning.duration").timer()).isNotNull();
        assertThat(registry.find("acs.caregiver.active").gauge()).isNotNull();
    }
}
