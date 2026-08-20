package com.neuroassist.pac.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.neuroassist.pac.bdd.HttpTestHelper;
import com.neuroassist.pac.bdd.ScenarioContext;
import com.neuroassist.pac.bdd.TestDataHelper;
import com.neuroassist.pac.integration.ExternalServicesWireMock;
import com.neuroassist.pac.integration.PacWireMockStubs;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Command Lifecycle (Draft → Validate → Publish → Recalibrate → Retire).
 * Feature: command-lifecycle.feature (PAC-025, P9).
 *
 * <p>Not annotated with @Component — registered exclusively via {@link
 * com.neuroassist.pac.bdd.BddTestConfiguration}.
 */
@RequiredArgsConstructor
public class CommandLifecycleSteps {

    private final ScenarioContext ctx;
    private final HttpTestHelper http;
    private final TestDataHelper data;

    // -------------------------------------------------------------------------
    // GEST stubs
    // -------------------------------------------------------------------------

    @Given("GEST knows primitive \"gst-001\"")
    public void givenGestKnowsPrimitiveGst001() {
        PacWireMockStubs.stubGestDetectability_Supported(ExternalServicesWireMock.gest());
    }

    // -------------------------------------------------------------------------
    // POST /api/pac/v1/pacientes/{pid}/comandos
    // -------------------------------------------------------------------------

    @When("^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos\" with body:")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandosWith(String body) {
        UUID patientId = ctx.getAlias("pac-001");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos";
        body = resolvePrimitiveIds(body);
        ResponseEntity<String> response = http.post(path, body);
        ctx.setLastResponse(response);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                String id = JsonPath.parse(response.getBody()).read("$.id");
                ctx.putAlias("last-command", UUID.fromString(id));
                ctx.setLastEtag(response.getHeaders().getETag());
            } catch (Exception ignored) {
            }
        }
    }

    @And(
            "^the response header \"Location\" matches \"/api/pac/v1/pacientes/pac-001/comandos/\\[0-9a-f-\\]\\+\"")
    public void andTheResponseHeaderLocationMatchesApipacv1pacientespac001comandos09af() {
        UUID patientId = ctx.getAlias("pac-001");
        String location = ctx.getLastResponse().getHeaders().getFirst("Location");
        assertThat(location)
                .as("Location should match /api/pac/v1/pacientes/{pid}/comandos/<uuid>")
                .matches("/api/pac/v1/pacientes/" + patientId + "/comandos/[0-9a-f-]+");
    }

    @And("the response body field \"estado\" is \"drafted\"")
    public void andTheResponseBodyFieldEstadoIsDrafted() {
        assertJsonField("estado", "drafted");
    }

    @And("the response body field \"calibracaoVersao\" is 0")
    public void andTheResponseBodyFieldCalibracaoversaoIs0() {
        assertJsonFieldInt("calibracaoVersao", 0);
    }

    // -------------------------------------------------------------------------
    // Duplicate active mapping
    // -------------------------------------------------------------------------

    @Given(
            "command \"cmd-001\" for patient \"pac-001\" maps \"gst-001\" to need \"hidratacao\" with estado \"published\"")
    public void givenCommandCmd001ForPatientPac001MapsGst001To() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID gst001 = TestDataHelper.primitiveUuid("gst-001");
        UUID id =
                data.createComando(
                        patientId,
                        ctx.getCurrentEmpresaId(),
                        "published",
                        "rotina",
                        0,
                        gst001,
                        null);
        ctx.putAlias("cmd-001", id);
    }

    @And("^the response body field \"type\" is \"/errors/duplicate-active-mapping\"")
    public void andTheResponseBodyFieldTypeIsErrorsduplicateactivemapping() {
        assertJsonField("type", "/errors/duplicate-active-mapping");
    }

    // -------------------------------------------------------------------------
    // VALIDATE transition
    // -------------------------------------------------------------------------

    @When(
            "^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-draft:validate\" with matching ETag")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandoscmddraftvalidateWith() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-draft");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":validate";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    @And("the response body field \"estado\" is \"validated\"")
    public void andTheResponseBodyFieldEstadoIsValidated() {
        assertJsonField("estado", "validated");
    }

    @Given(
            "command \"cmd-draft\" for patient \"pac-001\" references primitive \"gst-unknown\" with estado \"drafted\"")
    public void givenCommandCmddraftForPatientPac001ReferencesPrimitiveGstunknown() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID gstUnknown = TestDataHelper.primitiveUuid("gst-unknown");
        UUID id =
                data.createComando(
                        patientId,
                        ctx.getCurrentEmpresaId(),
                        "drafted",
                        "rotina",
                        0,
                        gstUnknown,
                        null);
        ctx.putAlias("cmd-draft", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("GEST returns 404 for primitive \"gst-unknown\"")
    public void andGestReturns404ForPrimitiveGstunknown() {
        // Stub GEST to return 404 for any detectability check (simulates unknown primitive)
        ExternalServicesWireMock.gest()
                .stubFor(
                        com.github.tomakehurst.wiremock.client.WireMock.post(
                                        com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo(
                                                "/api/gest/v1/detectability/check"))
                                .willReturn(
                                        com.github.tomakehurst.wiremock.client.WireMock.aResponse()
                                                .withStatus(404)));
    }

    // -------------------------------------------------------------------------
    // PUBLISH transition
    // -------------------------------------------------------------------------

    @When(
            "^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-val:publish\" with matching ETag")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandoscmdvalpublishWith() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-val");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":publish";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    @And("HIST is available")
    public void andHistIsAvailable() {
        PacWireMockStubs.stubHistAvailable(ExternalServicesWireMock.hist());
    }

    @And("HIST received a \"ComandoPublicado\" fact for \"cmd-val\"")
    public void andHistReceivedAComandopublicadoFactForCmdval() {
        // Verify WireMock received a POST to the HIST facts endpoint
        ExternalServicesWireMock.hist()
                .verify(
                        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                                        ".*facts.*")));
    }

    // -------------------------------------------------------------------------
    // Edit draft (PUT on published command)
    // -------------------------------------------------------------------------

    @And("^the response body field \"type\" is \"/errors/invalid-lifecycle-transition\"")
    public void andTheResponseBodyFieldTypeIsErrorsinvalidlifecycletransition() {
        assertJsonField("type", "/errors/invalid-lifecycle-transition");
    }

    // -------------------------------------------------------------------------
    // RECALIBRATE
    // -------------------------------------------------------------------------

    @Given(
            "command \"cmd-pub\" for patient \"pac-001\" exists with estado \"published\" and calibracaoVersao 2")
    public void givenCommandCmdpubForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID id =
                data.createComandoWithCalibracao(
                        patientId, ctx.getCurrentEmpresaId(), "published", 2);
        ctx.putAlias("cmd-pub", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("the response body field \"calibracaoVersao\" is 3")
    public void andTheResponseBodyFieldCalibracaoversaoIs3() {
        assertJsonFieldInt("calibracaoVersao", 3);
    }

    @And("the response body field \"sensibilidade\" is \"alta\"")
    public void andTheResponseBodyFieldSensibilidadeIsAlta() {
        assertJsonField("sensibilidade", "alta");
    }

    @Then("HIST received a \"ComandoRecalibrado\" fact for \"cmd-pub\"")
    public void thenHistReceivedAComandorecalibradoFactForCmdpub() {
        ExternalServicesWireMock.hist()
                .verify(
                        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                                        ".*facts.*")));
    }

    // -------------------------------------------------------------------------
    // RETIRE transition
    // -------------------------------------------------------------------------

    @When(
            "^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:retire\" with matching ETag")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandoscmdpubretireWith() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-pub");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":retire";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    @And("the response body field \"estado\" is \"retired\"")
    public void andTheResponseBodyFieldEstadoIsRetired() {
        assertJsonField("estado", "retired");
    }

    @And(
            "^a GET request to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-pub\" returns 200 with estado \"retired\"")
    public void andAGetRequestToApipacv1pacientespac001comandoscmdpubReturns200With() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-pub");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId;
        ResponseEntity<String> getResp = http.get(path);
        assertThat(getResp.getStatusCode().value()).isEqualTo(200);
        Object estado = JsonPath.parse(getResp.getBody()).read("$.estado");
        assertThat(estado).isEqualTo("retired");
    }

    @When(
            "^a POST request is made to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-ret:retire\" with matching ETag")
    public void whenAPostRequestIsMadeToApipacv1pacientespac001comandoscmdretretireWith() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-ret");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":retire";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
    }

    @Then("HIST received a \"ComandoAposentado\" fact for \"cmd-pub\"")
    public void thenHistReceivedAComandoaposentadoFactForCmdpub() {
        ExternalServicesWireMock.hist()
                .verify(
                        com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor(
                                com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching(
                                        ".*facts.*")));
    }

    // -------------------------------------------------------------------------
    // List commands
    // -------------------------------------------------------------------------

    @When("^a GET request is made to \"/api/pac/v1/pacientes/pac-001/comandos\"")
    public void whenAGetRequestIsMadeToApipacv1pacientespac001comandos() {
        UUID patientId = ctx.getAlias("pac-001");
        ResponseEntity<String> response =
                http.get("/api/pac/v1/pacientes/" + patientId + "/comandos");
        ctx.setLastResponse(response);
    }

    // NOTE: "the response list contains {string}" is handled generically by SharedSteps.andTheResponseListContains
    // to avoid AmbiguousStepDefinitionsException (D-532).

    // NOTE: "the response list does not contain {string}" is handled generically by SharedSteps.andTheResponseListDoesNotContain
    // to avoid AmbiguousStepDefinitionsException (D-532).

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Replaces primitive name aliases in JSON body with their deterministic UUIDs. */
    private static String resolvePrimitiveIds(String body) {
        for (String alias :
                new String[] {"gst-001", "gst-unknown", "gst-pending", "gst-primary"}) {
            body = body.replace("\"" + alias + "\"", "\"" + TestDataHelper.primitiveUuid(alias) + "\"");
        }
        return body;
    }

    private void assertJsonField(String field, Object expected) {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("Response body must not be null").isNotNull();
        Object actual = JsonPath.parse(body).read("$." + field);
        assertThat(actual).as("JSON field '%s'", field).isEqualTo(expected);
    }

    private void assertJsonFieldInt(String field, int expected) {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("Response body must not be null").isNotNull();
        Number actual = JsonPath.parse(body).read("$." + field);
        assertThat(actual.intValue()).as("JSON field '%s'", field).isEqualTo(expected);
    }
}
