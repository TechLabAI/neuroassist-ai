package com.neuroassist.evnt.bdd;

import com.neuroassist.evnt.application.port.out.AuditTrailPort;
import com.neuroassist.evnt.application.port.out.CaregiverRoutingPort;
import com.neuroassist.evnt.application.port.out.CommandLookupPort;
import com.neuroassist.evnt.application.port.out.GestValidationPort;
import com.neuroassist.evnt.application.port.out.NotificationDispatchPort;
import com.neuroassist.evnt.infrastructure.sse.SseEmitterAdapter;
import io.cucumber.spring.CucumberContextConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Bootstraps the Spring context for Cucumber step definitions.
 *
 * <p>Uses a singleton PostgreSQLContainer (P8 fix: EVNT-022). @MockBean declarations centralise all
 * port mocks so step definition classes can @Autowired them without creating additional contexts.
 */
@CucumberContextConfiguration
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestJwtDecoderConfig.class)
public class CucumberSpringContext {

    // ---------------------------------------------------------------------------
    // Singleton Testcontainers PostgreSQL — same pattern as BaseIntegrationTest
    // ---------------------------------------------------------------------------

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        POSTGRES =
                new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("neuroassist-ai_test")
                        .withUsername("test")
                        .withPassword("test")
                        .withInitScript("init-schema.sql");
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    // ---------------------------------------------------------------------------
    // Port mocks shared by all step definition classes (P8: EVNT-022/023/024)
    // ---------------------------------------------------------------------------

    /**
     * Replaces GestValidationAdapter so steps can control validation outcomes per scenario. Default
     * Mockito mock returns false — each scenario must stub the expected behaviour.
     */
    @MockBean GestValidationPort gestValidationPort;

    /** Replaces CommandLookupHardcodedAdapter so steps can inject command/urgency per scenario. */
    @MockBean CommandLookupPort commandLookupPort;

    /** Replaces CaregiverRoutingHardcodedAdapter so steps can control caregiver resolution. */
    @MockBean CaregiverRoutingPort caregiverRoutingPort;

    /**
     * Replaces NotificationDispatchLogAdapter so steps can simulate DASH availability. Default mock
     * is a no-op (dispatch succeeds silently).
     */
    @MockBean NotificationDispatchPort notificationDispatchPort;

    /** Replaces AuditTrailNoOpAdapter so steps can verify audit calls via Mockito.verify(). */
    @MockBean AuditTrailPort auditTrailPort;

    /**
     * Spies on SseEmitterAdapter (P8 fix: EVNT-024). LiveEventDeliverySteps verify that broadcast()
     * was invoked with the expected Evento, avoiding the need for a real SSE client connection in
     * BDD tests. The real adapter is still used for registration/backfill.
     */
    @SpyBean SseEmitterAdapter sseEmitterAdapter;
}
