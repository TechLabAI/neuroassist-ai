package com.neuroassist.pac.bdd;

import com.neuroassist.pac.bdd.steps.CatalogPublicationSteps;
import com.neuroassist.pac.bdd.steps.CommandLifecycleSteps;
import com.neuroassist.pac.bdd.steps.EmergencyEligibilityGateSteps;
import com.neuroassist.pac.bdd.steps.PatientRegistrationSteps;
import com.neuroassist.pac.bdd.steps.SharedSteps;
import com.neuroassist.pac.infrastructure.persistence.repository.ComandoJpaRepository;
import com.neuroassist.pac.infrastructure.persistence.repository.PacienteJpaRepository;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * @TestConfiguration that registers all BDD-specific beans in isolation.
 *
 * <p>These beans are ONLY loaded when this configuration is explicitly imported (via {@link
 * CucumberSpringContext}). They are never picked up by the default component scan, so they cannot
 * contaminate other Spring test contexts — in particular SecurityConfigTest which uses {@code
 * webEnvironment=MOCK} and has no {@link TestRestTemplate} available.
 *
 * <p>PAC-025 / P9 isolation fix.
 */
@TestConfiguration
public class BddTestConfiguration {

    // -------------------------------------------------------------------------
    // Core BDD infrastructure beans
    // -------------------------------------------------------------------------

    /**
     * Per-scenario state holder. Spring applies @Value injection (localServerPort) via
     * AutowiredAnnotationBeanPostProcessor after creation.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public ScenarioContext scenarioContext() {
        return new ScenarioContext();
    }

    /**
     * HTTP helper that wraps TestRestTemplate with JWT/header injection. Requires
     * webEnvironment=RANDOM_PORT — only available in BDD context.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public HttpTestHelper httpTestHelper(
            TestRestTemplate restTemplate, ScenarioContext scenarioContext) {
        return new HttpTestHelper(restTemplate, scenarioContext);
    }

    /** Cucumber lifecycle hooks (reset ScenarioContext + WireMock between scenarios). */
    @Bean
    @org.springframework.context.annotation.Primary
    public BddHooks bddHooks(ScenarioContext scenarioContext) {
        return new BddHooks(scenarioContext);
    }

    /**
     * Direct JPA fixture helper — bypasses service layer for Given steps. JPA repos are injected by
     * Spring from the application context.
     */
    @Bean
    @org.springframework.context.annotation.Primary
    public TestDataHelper testDataHelper(
            PacienteJpaRepository pacienteRepo,
            ComandoJpaRepository comandoRepo,
            TransactionTemplate transactionTemplate) {
        return new TestDataHelper(pacienteRepo, comandoRepo, transactionTemplate);
    }

    // -------------------------------------------------------------------------
    // Step definition beans
    // -------------------------------------------------------------------------

    @Bean
    @org.springframework.context.annotation.Primary
    public SharedSteps sharedSteps(ScenarioContext ctx, HttpTestHelper http, TestDataHelper data) {
        return new SharedSteps(ctx, http, data);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public PatientRegistrationSteps patientRegistrationSteps(
            ScenarioContext ctx, HttpTestHelper http, TestDataHelper data) {
        return new PatientRegistrationSteps(ctx, http, data);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public CommandLifecycleSteps commandLifecycleSteps(
            ScenarioContext ctx, HttpTestHelper http, TestDataHelper data) {
        return new CommandLifecycleSteps(ctx, http, data);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public EmergencyEligibilityGateSteps emergencyEligibilityGateSteps(
            ScenarioContext ctx, HttpTestHelper http, TestDataHelper data) {
        return new EmergencyEligibilityGateSteps(ctx, http, data);
    }

    /** E2E-015 — propagação do vínculo cuidador-paciente para o directory do ACS. */
    @Bean
    @org.springframework.context.annotation.Primary
    public com.neuroassist.pac.bdd.steps.DirectoryPropagationSteps directoryPropagationSteps(
            ScenarioContext ctx,
            com.neuroassist.pac.infrastructure.persistence.repository.DirectoryOutboxJpaRepository
                    outboxRepository,
            com.neuroassist.pac.application.port.in.DirectoryOutboxDrainUseCase drainUseCase,
            TransactionTemplate transactionTemplate) {
        return new com.neuroassist.pac.bdd.steps.DirectoryPropagationSteps(
                ctx, outboxRepository, drainUseCase, transactionTemplate);
    }

    @Bean
    @org.springframework.context.annotation.Primary
    public CatalogPublicationSteps catalogPublicationSteps(
            ScenarioContext ctx, HttpTestHelper http, TestDataHelper data) {
        return new CatalogPublicationSteps(ctx, http, data);
    }
}
