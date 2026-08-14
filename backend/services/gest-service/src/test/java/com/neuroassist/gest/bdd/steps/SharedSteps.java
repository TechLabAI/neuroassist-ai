package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.bdd.BddTestContext;
import com.neuroassist.gest.bdd.TestJwtFactory;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Shared step definitions — steps common to two or more feature files.
 *
 * <p>P8 implementation: replaces all PendingException stubs with real logic.
 */
public class SharedSteps {

    @Autowired private BddTestContext ctx;

    @Autowired private EdgePairingPort edgePairingPort;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Lifecycle hooks ───────────────────────────────────────────────────────

    /**
     * Runs before every Cucumber scenario: 1. Cleans all GEST data from both test schemas
     * (idempotent). 2. Resets the BddTestContext state fields. 3. Resets the EdgePairingPort
     * Mockito mock (stubs and recorded invocations).
     */
    @Before
    public void setupScenario() {
        ctx.cleanSchema("t_emp_test");
        ctx.cleanSchema("t_emp_other");
        ctx.reset();
        Mockito.reset(edgePairingPort);
    }

    // ── Background steps ──────────────────────────────────────────────────────

    @Given("the tenant \"empresa-test\" is active")
    public void givenTheTenantEmpresatestIsActive() {
        ctx.setCurrentTenant(ctx.mapTenantName("empresa-test"));
        ctx.setCurrentToken(TestJwtFactory.buildManageToken(ctx.getCurrentTenant()));
    }

    // ── Auth steps ────────────────────────────────────────────────────────────

    @And("the caller has role \"gest.read\"")
    public void andTheCallerHasRoleGestread() {
        ctx.setCurrentToken(TestJwtFactory.buildReadToken(ctx.getCurrentTenant()));
    }

    // ── HTTP status assertions ─────────────────────────────────────────────────
    // One method per status code (avoids ambiguity with parameterised {int} variants).

    @Then("the response status is 200")
    public void thenResponseStatusIs200() {
        assertThat(ctx.getLastStatus())
                .as(
                        "Expected HTTP 200 but got %d. Body: %s",
                        ctx.getLastStatus(), ctx.getLastBody())
                .isEqualTo(200);
    }

    @Then("the response status is 201")
    public void thenResponseStatusIs201() {
        assertThat(ctx.getLastStatus())
                .as(
                        "Expected HTTP 201 but got %d. Body: %s",
                        ctx.getLastStatus(), ctx.getLastBody())
                .isEqualTo(201);
    }

    @Then("the response status is 403")
    public void thenResponseStatusIs403() {
        assertThat(ctx.getLastStatus())
                .as(
                        "Expected HTTP 403 but got %d. Body: %s",
                        ctx.getLastStatus(), ctx.getLastBody())
                .isEqualTo(403);
    }

    @Then("the response status is 422")
    public void thenResponseStatusIs422() {
        assertThat(ctx.getLastStatus())
                .as(
                        "Expected HTTP 422 but got %d. Body: %s",
                        ctx.getLastStatus(), ctx.getLastBody())
                .isEqualTo(422);
    }

    // ── Error type assertions (ProblemDetail RFC 7807) ────────────────────────
    // Each annotation is an exact Cucumber Expression matching the feature step literal.

    @And("the error type is \"\\/errors\\/insufficient-scope\"")
    public void andErrorTypeIsInsufficientScope() throws Exception {
        assertErrorType("/errors/insufficient-scope");
    }

    @And("the error type is \"\\/errors\\/validation\"")
    public void andErrorTypeIsValidation() throws Exception {
        assertErrorType("/errors/validation");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Asserts that the {@code type} field of the ProblemDetail response body ends with the given
     * suffix (e.g. {@code "/errors/insufficient-scope"}).
     */
    void assertErrorType(String expectedSuffix) throws Exception {
        JsonNode json = objectMapper.readTree(ctx.getLastBody());
        String actualType = json.path("type").asText("");
        assertThat(actualType)
                .as("Expected error type to contain '%s' but was '%s'", expectedSuffix, actualType)
                .contains(expectedSuffix);
    }
}
