package com.neuroassist.acs.bdd;

import io.cucumber.spring.ScenarioScope;
import jakarta.annotation.PostConstruct;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Scenario-scoped state shared across all step definition classes (P5 / ACS-024).
 *
 * <p>Cucumber-Spring resets this bean between scenarios (ScenarioScope). Step classes autowire this
 * to share HTTP responses, test entity UUIDs, and authentication tokens.
 *
 * <p>Fields are intentionally public to minimise boilerplate — this is test-only infrastructure.
 */
@Component
@ScenarioScope
public class BddContext {

    @PostConstruct
    public void initializeIds() {
        // Ensure UUIDs are initialized after Spring creates this bean
        if (this.testTenantId == null) this.testTenantId = UUID.randomUUID();
        if (this.testTenantId2 == null) this.testTenantId2 = UUID.randomUUID();
        if (this.testCaregiverId == null) this.testCaregiverId = UUID.randomUUID();
        if (this.secondCaregiverId == null) this.secondCaregiverId = UUID.randomUUID();
        if (this.conductorId == null) this.conductorId = UUID.randomUUID();
        if (this.testPatientId == null) this.testPatientId = UUID.randomUUID();
        if (this.secondPatientId == null) this.secondPatientId = UUID.randomUUID();
    }

    // -----------------------------------------------------------------------
    // HTTP response — captured by When steps, asserted by Then steps
    // -----------------------------------------------------------------------

    /** Raw JSON response from the last HTTP call. */
    public ResponseEntity<String> lastResponse;

    /** Elapsed time of the last HTTP call in milliseconds (for performance assertions). */
    public long lastResponseTimeMs;

    // -----------------------------------------------------------------------
    // Test tenant state
    // -----------------------------------------------------------------------

    /** Primary test tenant UUID (represents "t_0f8a" in feature files). */
    public UUID testTenantId;

    /** Secondary test tenant UUID (represents "t_1111" in feature files). */
    public UUID testTenantId2;

    /** Tenant under provisioning retry test. */
    public UUID retryTenantId;

    // -----------------------------------------------------------------------
    // Test caregiver state
    // -----------------------------------------------------------------------

    /** Primary caregiver UUID ("5c1a" in feature files). */
    public UUID testCaregiverId;

    /** Secondary caregiver UUID ("6d2b" in feature files). */
    public UUID secondCaregiverId;

    /** Platform conductor UUID (for tenant provisioning scenarios). */
    public UUID conductorId;

    // -----------------------------------------------------------------------
    // Test patient state
    // -----------------------------------------------------------------------

    /** Primary patient UUID ("p_9a3f" in feature files). */
    public UUID testPatientId;

    /** Secondary patient UUID ("p_1b2c" in feature files). */
    public UUID secondPatientId;

    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------

    /** JWT access token for the current authenticated actor. */
    public String bearerToken;

    /** Raw password stored for auth test scenarios ("no raw password in response" assertion). */
    public String lastRawPassword = "Test@1234!";
}
