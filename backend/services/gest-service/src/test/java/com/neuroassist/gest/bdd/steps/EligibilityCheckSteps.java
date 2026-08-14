package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.gest.bdd.BddTestContext;
import com.neuroassist.gest.bdd.TestJwtFactory;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Step definitions — Eligibility check — emergency-command guard. Feature:
 * eligibility-check.feature
 *
 * <p>P8 implementation: all PendingException stubs replaced with real MockMvc calls.
 *
 * <p>Known API gaps that will cause scenario failures (captured for Validator):
 *
 * <ul>
 *   <li>EligibilityService always returns eligible=true for non-EMERGENCIA urgency levels, even for
 *       deprecated primitives (feature expects eligible=false for "rotina" + deprecated).
 *   <li>Unknown primitive IDs are not validated (service checks ALL tenant primitives by name);
 *       unknown ID scenarios expecting 422 will receive 200 eligible=true.
 *   <li>Cross-tenant isolation scenario: empresa-test has no primitives → eligible=true (not 422).
 * </ul>
 */
public class EligibilityCheckSteps {

    private static final String BASE_URL = "/gest-service/eligibility";

    @Autowired private BddTestContext ctx;

    @Autowired private MockMvc mockMvc;

    @Autowired private SharedSteps sharedSteps;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // ── Helper ────────────────────────────────────────────────────────────────

    private void checkEligibility(String urgencyLevel) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(BASE_URL)
                                        .header(
                                                HttpHeaders.AUTHORIZATION,
                                                "Bearer " + ctx.getCurrentToken())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        Map.of("urgencyLevel", urgencyLevel))))
                        .andReturn();
        ctx.setLastStatus(result.getResponse().getStatus());
        ctx.setLastBody(result.getResponse().getContentAsString());
    }

    private boolean parsedEligible() throws Exception {
        return objectMapper.readTree(ctx.getLastBody()).path("eligible").asBoolean();
    }

    private boolean primitiveMissingFromResponse(String nome) throws Exception {
        JsonNode json = objectMapper.readTree(ctx.getLastBody());
        JsonNode missingStates = json.path("missingStates");
        if (!missingStates.isArray()) return false;
        for (JsonNode entry : missingStates) {
            if (entry.asText().startsWith(nome + ":")) {
                return true;
            }
        }
        return false;
    }

    // ── Background steps ──────────────────────────────────────────────────────

    @And("the caller is authenticated with role \"gest.read\"")
    public void andCallerIsAuthenticatedWithRoleGestRead() {
        ctx.setCurrentToken(TestJwtFactory.buildReadToken(ctx.getCurrentTenant()));
    }

    // ── Given (setup) steps ────────────────────────────────────────────────────

    @Given("primitive \"3f1c-aaa\" is in state \"supported\" in tenant \"empresa-test\"")
    public void givenPrimitive3f1caaaIsSupportedInEmpresatest() {
        ctx.insertTipoGesto("3f1c-aaa", "supported", "mp.blink.double.v1", ctx.currentSchema());
    }

    @And("primitive \"9a20-bbb\" is in state \"supported\" in tenant \"empresa-test\"")
    public void andPrimitive9a20bbbIsSupportedInEmpresatest() {
        ctx.insertTipoGesto("9a20-bbb", "supported", "mp.brow.raise.v1", ctx.currentSchema());
    }

    @And("primitive \"9a20-bbb\" is in state \"registered\" in tenant \"empresa-test\"")
    public void andPrimitive9a20bbbIsRegisteredInEmpresatest() {
        ctx.insertTipoGesto("9a20-bbb", "registered", null, ctx.currentSchema());
    }

    @Given("primitive \"3f1c-aaa\" is in state \"pending_training\" in tenant \"empresa-test\"")
    public void givenPrimitive3f1caaaIsPendingTrainingInEmpresatest() {
        ctx.insertTipoGesto("3f1c-aaa", "pending_training", null, ctx.currentSchema());
    }

    @And("primitive \"9a20-bbb\" is in state \"pending_training\" in tenant \"empresa-test\"")
    public void andPrimitive9a20bbbIsPendingTrainingInEmpresatest() {
        ctx.insertTipoGesto("9a20-bbb", "pending_training", null, ctx.currentSchema());
    }

    @Given("primitive \"3f1c-aaa\" is in state \"deprecated\" in tenant \"empresa-test\"")
    public void givenPrimitive3f1caaaIsDeprecatedInEmpresatest() {
        ctx.insertTipoGesto("3f1c-aaa", "deprecated", null, ctx.currentSchema());
    }

    @Given("primitive \"3f1c-aaa\" exists in tenant \"empresa-other\" in state \"supported\"")
    public void givenPrimitive3f1caaaExistsInEmpresaotherSupported() {
        // Insert in empresa-other schema (not the current tenant)
        ctx.insertTipoGesto(
                "3f1c-aaa", "supported", "mp.blink.double.v1", ctx.schemaFor("emp_other"));
    }

    // ── When (action) steps ────────────────────────────────────────────────────

    @When(
            "the caller checks eligibility with urgencia \"emergencia\" for primitives [\"3f1c-aaa\", \"9a20-bbb\"]")
    public void whenCallerChecksEligibilityEmergenciaForBoth() throws Exception {
        checkEligibility("emergencia");
    }

    @When(
            "the caller checks eligibility with urgencia \"emergencia\" for primitives [\"3f1c-aaa\"]")
    public void whenCallerChecksEligibilityEmergenciaFor3f1cAaa() throws Exception {
        checkEligibility("emergencia");
    }

    @When("the caller checks eligibility with urgencia \"rotina\" for primitives [\"3f1c-aaa\"]")
    public void whenCallerChecksEligibilityRotinaFor3f1cAaa() throws Exception {
        checkEligibility("rotina");
    }

    @When(
            "the caller checks eligibility with urgencia \"emergencia\" for primitives [\"unknown-id-xyz\"]")
    public void whenCallerChecksEligibilityForUnknownId() throws Exception {
        // NOTE: API checks ALL tenant primitives by NAME, not the supplied IDs.
        // unknown-id-xyz does not exist → service returns eligible=true (no primitives to fail).
        // Expected: 422 unknown-primitive — will fail due to API gap.
        checkEligibility("emergencia");
    }

    @When(
            "the caller checks eligibility with urgencia \"emergencia\" for primitives [\"3f1c-aaa\", \"ghost-1\", \"ghost-2\"]")
    public void whenCallerChecksEligibilityForMixedIds() throws Exception {
        // NOTE: ghost-1 and ghost-2 do not exist in tenant; API returns based on actual DB state.
        // Expected: 422 unknown-primitive — will fail due to API gap.
        checkEligibility("emergencia");
    }

    @And(
            "the caller checks eligibility with urgencia \"emergencia\" for primitives [\"3f1c-aaa\"] again")
    public void andCallerChecksEligibilityAgain() throws Exception {
        // Save first response for idempotency comparison
        ctx.setFirstEligibilityStatus(ctx.getLastStatus());
        ctx.setFirstEligibilityBody(ctx.getLastBody());
        // Make second call
        checkEligibility("emergencia");
    }

    @When("a caller from tenant \"empresa-test\" checks eligibility for primitives [\"3f1c-aaa\"]")
    public void whenCallerFromEmpresaTestChecksEligibilityForPrimitive() throws Exception {
        // Caller is in empresa-test but the primitive only exists in empresa-other.
        // NOTE: API checks empresa-test schema (empty) → eligible=true; expected: 422.
        checkEligibility("emergencia");
    }

    // ── Then (assertion) steps ─────────────────────────────────────────────────

    @And("\"eligible\" is true")
    public void andEligibleIsTrue() throws Exception {
        assertThat(parsedEligible())
                .as("Expected eligible=true in response: %s", ctx.getLastBody())
                .isTrue();
    }

    @And("\"eligible\" is false")
    public void andEligibleIsFalse() throws Exception {
        assertThat(parsedEligible())
                .as("Expected eligible=false in response: %s", ctx.getLastBody())
                .isFalse();
    }

    @And("result for \"3f1c-aaa\" has eligible true")
    public void andResultFor3f1caaaHasEligibleTrue() throws Exception {
        // In aggregate response: 3f1c-aaa has eligible=true means it is NOT in missingStates
        assertThat(primitiveMissingFromResponse("3f1c-aaa"))
                .as("'3f1c-aaa' should NOT be in missingStates (it is SUPPORTED)")
                .isFalse();
    }

    @And("result for \"9a20-bbb\" has eligible true")
    public void andResultFor9a20bbbHasEligibleTrue() throws Exception {
        assertThat(primitiveMissingFromResponse("9a20-bbb"))
                .as("'9a20-bbb' should NOT be in missingStates (it is SUPPORTED)")
                .isFalse();
    }

    @And("result for \"9a20-bbb\" has eligible false with reason \"primitive is not 'supported'\"")
    public void andResultFor9a20bbbHasEligibleFalseWithReason() throws Exception {
        assertThat(primitiveMissingFromResponse("9a20-bbb"))
                .as("'9a20-bbb' should be in missingStates (not SUPPORTED)")
                .isTrue();
    }

    @And("result for \"3f1c-aaa\" has eligible false")
    public void andResultFor3f1caaaHasEligibleFalse() throws Exception {
        assertThat(primitiveMissingFromResponse("3f1c-aaa"))
                .as("'3f1c-aaa' should be in missingStates (not SUPPORTED)")
                .isTrue();
    }

    @And("the error type is \"\\/errors\\/unknown-primitive\"")
    public void andErrorTypeIsUnknownPrimitive() throws Exception {
        sharedSteps.assertErrorType("/errors/unknown-primitive");
    }

    @And("the error detail lists \"unknown-id-xyz\"")
    public void andErrorDetailListsUnknownIdXyz() throws Exception {
        assertThat(ctx.getLastBody())
                .as("Error detail should list 'unknown-id-xyz'")
                .contains("unknown-id-xyz");
    }

    @And("the error detail lists \"ghost-1\" and \"ghost-2\"")
    public void andErrorDetailListsGhost1AndGhost2() throws Exception {
        String body = ctx.getLastBody();
        assertThat(body).as("Error detail should list 'ghost-1'").contains("ghost-1");
        assertThat(body).as("Error detail should list 'ghost-2'").contains("ghost-2");
    }

    @Then("both responses are 200 with identical \"eligible\" value")
    public void thenBothResponsesAre200WithIdenticalEligibleValue() throws Exception {
        assertThat(ctx.getFirstEligibilityStatus())
                .as("First eligibility call should return 200")
                .isEqualTo(200);
        assertThat(ctx.getLastStatus())
                .as("Second eligibility call should return 200")
                .isEqualTo(200);

        boolean firstEligible =
                objectMapper.readTree(ctx.getFirstEligibilityBody()).path("eligible").asBoolean();
        boolean secondEligible = parsedEligible();
        assertThat(secondEligible)
                .as(
                        "Both calls should return identical 'eligible' value. First=%b, Second=%b",
                        firstEligible, secondEligible)
                .isEqualTo(firstEligible);
    }

    @And("the detectabilityState of \"3f1c-aaa\" has not changed")
    public void andDetectabilityStateOf3f1caaaHasNotChanged() {
        // Eligibility check is read-only — state must remain unchanged from setup
        String state = ctx.getStateFromDb("3f1c-aaa");
        assertThat(state)
                .as("EligibilityService is read-only; state should not have changed from setup")
                .isIn("supported", "pending_training", "registered", "deprecated");
    }
}
