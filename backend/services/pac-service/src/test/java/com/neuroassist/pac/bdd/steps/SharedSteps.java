package com.neuroassist.pac.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.neuroassist.pac.bdd.HttpTestHelper;
import com.neuroassist.pac.bdd.ScenarioContext;
import com.neuroassist.pac.bdd.TestDataHelper;
import com.neuroassist.pac.bdd.TestJwtHelper;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

/**
 * Shared step definitions — steps common to two or more feature files (PAC-025, P9).
 *
 * <p>D-532: consolidated generic parameterised steps here to avoid
 * AmbiguousStepDefinitionsException. Feature-specific steps live in their respective *Steps
 * classes.
 *
 * <p>Not annotated with @Component — registered exclusively via {@link
 * com.neuroassist.pac.bdd.BddTestConfiguration}.
 */
@RequiredArgsConstructor
public class SharedSteps {

    private final ScenarioContext ctx;
    private final HttpTestHelper http;
    private final TestDataHelper data;

    // -------------------------------------------------------------------------
    // Background setup
    // -------------------------------------------------------------------------

    @Given("the PAC service is running")
    public void givenThePacServiceIsRunning() {
        // No-op: Spring Boot test context startup already verifies the service is running.
        // Real validation happens in subsequent steps when actual API calls are made.
    }

    @And("the request header \"X-Empresa-Id\" is \"empresa-abc\"")
    public void andTheRequestHeaderXempresaidIsEmpresaabc() {
        ctx.setCurrentEmpresaId("empresa-abc");
        ctx.setSendEmpresaId(true);
    }

    @And("a valid JWT bearer token is present with tenant claim \"empresa-abc\"")
    public void andAValidJwtBearerTokenIsPresentWith() {
        ctx.setCurrentEmpresaId("empresa-abc");
        ctx.setCurrentJwt(TestJwtHelper.createToken("empresa-abc"));
        ctx.setSendEmpresaId(true);
    }

    // -------------------------------------------------------------------------
    // Test data setup — patient
    // -------------------------------------------------------------------------

    /** Generic: creates a patient in the given empresa. */
    @Given("patient {string} exists in {string}")
    public void givenPatientExistsIn(String alias, String empresaId) {
        UUID id = data.createPaciente(alias, empresaId);
        ctx.putAlias(alias, id);
        ctx.setLastEtag("\"1\"");
        // Set current empresa context for subsequent requests in this scenario
        if (ctx.getCurrentEmpresaId() == null) {
            ctx.setCurrentEmpresaId(empresaId);
            ctx.setCurrentJwt(TestJwtHelper.createToken(empresaId));
        }
    }

    // -------------------------------------------------------------------------
    // Test data setup — command state helpers
    // -------------------------------------------------------------------------

    @Given("command \"cmd-pub\" for patient \"pac-001\" exists with estado \"published\"")
    public void givenCommandCmdpubForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        assertThat(patientId).as("pac-001 must be set up before cmd-pub").isNotNull();
        UUID id =
                data.createComando(
                        patientId,
                        ctx.getCurrentEmpresaId(),
                        "published",
                        "rotina",
                        0,
                        TestDataHelper.primitiveUuid("gst-001"),
                        null);
        ctx.putAlias("cmd-pub", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("command \"cmd-draft\" for patient \"pac-001\" exists with estado \"drafted\"")
    public void andCommandCmddraftForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        assertThat(patientId).as("pac-001 must be set up before cmd-draft").isNotNull();
        UUID id = data.createComando(patientId, ctx.getCurrentEmpresaId(), "drafted");
        ctx.putAlias("cmd-draft", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("command \"cmd-ret\" for patient \"pac-001\" exists with estado \"retired\"")
    public void andCommandCmdretForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        assertThat(patientId).as("pac-001 must be set up before cmd-ret").isNotNull();
        UUID id = data.createComando(patientId, ctx.getCurrentEmpresaId(), "retired");
        ctx.putAlias("cmd-ret", id);
        ctx.setLastEtag("\"1\"");
    }

    @And(
            "command \"cmd-val\" for patient \"pac-001\" exists with estado \"validated\" and urgencia \"rotina\"")
    public void andCommandCmdvalForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        assertThat(patientId).as("pac-001 must be set up before cmd-val").isNotNull();
        UUID id = data.createComando(patientId, ctx.getCurrentEmpresaId(), "validated", "rotina");
        ctx.putAlias("cmd-val", id);
        ctx.setLastEtag("\"1\"");
    }

    // -------------------------------------------------------------------------
    // Recalibrate on cmd-pub (shared across features)
    // -------------------------------------------------------------------------

    @When(
            "^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:recalibrate\" with matching ETag and body:")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandoscmdpubrecalibrateWith(
            String body) {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-pub");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":recalibrate";
        ResponseEntity<String> response = http.postWithEtagAndBody(path, ctx.getLastEtag(), body);
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    // -------------------------------------------------------------------------
    // PUT (generic)
    // -------------------------------------------------------------------------

    @When("a PUT request is made to {string} with matching ETag and body:")
    public void whenAPutRequestIsMadeToWith(String path, String body) {
        // Resolve any aliases in the path
        String resolvedPath = resolvePath(path);
        // Resolve primitive aliases in the body (e.g. "gst-001" → UUID)
        String resolvedBody = resolvePrimitiveIds(body);
        ResponseEntity<String> response = http.put(resolvedPath, resolvedBody, ctx.getLastEtag());
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    // -------------------------------------------------------------------------
    // Response assertions (generic)
    // -------------------------------------------------------------------------

    @Then("the response status is {int}")
    public void thenTheResponseStatusIs(int status) {
        assertThat(ctx.getLastResponse()).as("No HTTP response recorded").isNotNull();
        assertThat(ctx.getLastResponse().getStatusCode().value())
                .as(
                        "Expected HTTP status %d but got %d. Response body: %s",
                        status,
                        ctx.getLastResponse().getStatusCode().value(),
                        ctx.getLastResponse().getBody())
                .isEqualTo(status);
    }

    @And("^the response body field \"type\" is \"/errors/validation\"")
    public void andTheResponseBodyFieldTypeIsErrorsvalidation() {
        assertJsonField("type", "/errors/validation");
    }

    @And("the response body field \"estado\" is \"published\"")
    public void andTheResponseBodyFieldEstadoIsPublished() {
        assertJsonField("estado", "published");
    }

    @And("the response header {string} is {string}")
    public void andTheResponseHeaderIs(String header, String value) {
        assertThat(ctx.getLastResponse().getHeaders())
                .as("Response should contain header %s", header)
                .satisfies(
                        h -> {
                            var headerValues = h.get(header);
                            assertThat(headerValues).isNotNull().isNotEmpty();
                            assertThat(headerValues.get(0)).isEqualTo(value);
                        });
    }

    // -------------------------------------------------------------------------
    // Response list assertions
    // -------------------------------------------------------------------------

    @And("the response list contains {string}")
    public void andTheResponseListContains(String alias) {
        String body = ctx.getLastResponse().getBody();
        UUID id = ctx.getAlias(alias);
        String idStr = id != null ? id.toString() : alias;
        assertThat(body).as("Response body should contain id for alias %s", alias).contains(idStr);
    }

    @And("the response list does not contain {string}")
    public void andTheResponseListDoesNotContain(String alias) {
        String body = ctx.getLastResponse().getBody();
        UUID id = ctx.getAlias(alias);
        String idStr = id != null ? id.toString() : alias;
        assertThat(body)
                .as("Response body should NOT contain id for alias %s", alias)
                .doesNotContain(idStr);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    protected void assertJsonField(String field, Object expectedValue) {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("Response body must not be null").isNotNull();
        Object actual = JsonPath.parse(body).read("$." + field);
        assertThat(actual)
                .as("JSON field '%s' expected <%s>", field, expectedValue)
                .isEqualTo(expectedValue);
    }

    private static String resolvePrimitiveIds(String body) {
        for (String alias : new String[] {"gst-001", "gst-unknown", "gst-pending", "gst-primary"}) {
            body =
                    body.replace(
                            "\"" + alias + "\"", "\"" + TestDataHelper.primitiveUuid(alias) + "\"");
        }
        return body;
    }

    /**
     * Resolves a path that may contain literal strings like "/api/pac/v1/pacientes/pac-001" by
     * substituting known aliases with their UUIDs.
     */
    private String resolvePath(String path) {
        // Try to resolve common alias patterns in the path
        for (String alias :
                new String[] {
                    "pac-001",
                    "pac-002",
                    "pac-valid",
                    "cmd-pub",
                    "cmd-draft",
                    "cmd-val",
                    "cmd-ret",
                    "cmd-emg",
                    "cmd-rot",
                    "cmd-composite",
                    "cmd-001"
                }) {
            UUID id = ctx.getAlias(alias);
            if (id != null) {
                path = path.replace("/" + alias, "/" + id);
            }
        }
        return path;
    }
}
