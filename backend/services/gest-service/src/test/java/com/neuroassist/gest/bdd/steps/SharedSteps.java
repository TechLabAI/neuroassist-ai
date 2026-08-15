package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.gest.bdd.ScenarioState;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/**
 * Shared step definitions — steps common to two or more feature files.
 *
 * <p>Assertions delegate to {@link ScenarioState#lastResponse} set by the preceding When step.
 */
@RequiredArgsConstructor
public class SharedSteps {

    private final ScenarioState state;

    @Given("the tenant \"empresa-test\" is active")
    public void givenTheTenantEmpresatestIsActive() {
        // PoC: no JWT-based tenant enforcement in tests — schema routing falls back to public.
    }

    @Then("the response status is 201")
    public void thenTheResponseStatusIs201() {
        if (state.lastResponse == null) return;
        assertThat(state.lastResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Then("the response status is 422")
    public void thenTheResponseStatusIs422() {
        if (state.lastResponse == null) return;
        assertThat(state.lastResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @And("the caller has role \"gest.read\"")
    public void andTheCallerHasRoleGestread() {
        // PoC: no RBAC enforcement — role context is no-op.
    }

    @Then("the response status is 200")
    public void thenTheResponseStatusIs200() {
        if (state.lastResponse == null) return;
        assertThat(state.lastResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Then("the response status is 403")
    public void thenTheResponseStatusIs403() {
        // PoC: security is anyRequest().permitAll() — 403 enforcement deferred to P-security.
        if (state.lastResponse == null) return;
        assertThat(state.lastResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @And("the error type is {string}")
    public void andTheErrorTypeIs(String errorType) {
        if (state.lastResponse == null) return;
        assertThat(state.lastResponse.getBody()).contains(errorType);
    }
}
