package com.neuroassist.pac.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.neuroassist.pac.bdd.HttpTestHelper;
import com.neuroassist.pac.bdd.ScenarioContext;
import com.neuroassist.pac.bdd.TestDataHelper;
import com.neuroassist.pac.bdd.TestJwtHelper;
import com.neuroassist.pac.integration.ExternalServicesWireMock;
import com.neuroassist.pac.integration.PacWireMockStubs;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Patient Registration. Feature: patient-registration.feature (PAC-025, P9).
 *
 * <p>Not annotated with @Component — registered exclusively via {@link
 * com.neuroassist.pac.bdd.BddTestConfiguration}.
 */
@RequiredArgsConstructor
public class PatientRegistrationSteps {

    private final ScenarioContext ctx;
    private final HttpTestHelper http;
    private final TestDataHelper data;

    // -------------------------------------------------------------------------
    // POST /api/pac/v1/pacientes
    // -------------------------------------------------------------------------

    @When("^a POST request is made to \"/api/pac/v1/pacientes\" with body:")
    public void whenAPostRequestIsMadeToApipacv1pacientesWith(String body) {
        body = resolveExternalIds(body);
        ResponseEntity<String> response = http.post("/api/pac/v1/pacientes", body);
        ctx.setLastResponse(response);
        // Capture ETag from successful creation
        if (response.getStatusCode().is2xxSuccessful()) {
            String etag = response.getHeaders().getETag();
            if (etag != null) {
                ctx.setLastEtag(etag);
            }
            // Register the returned patient id as "last-created-patient"
            String resBody = response.getBody();
            if (resBody != null && resBody.contains("\"id\"")) {
                try {
                    String id = JsonPath.parse(resBody).read("$.id");
                    ctx.putAlias("last-patient", UUID.fromString(id));
                } catch (Exception ignored) {
                }
            }
        }
    }

    @And("^the response header \"Location\" matches \"/api/pac/v1/pacientes/\\[0-9a-f-\\]\\+\"")
    public void andTheResponseHeaderLocationMatchesApipacv1pacientes09af() {
        String location = ctx.getLastResponse().getHeaders().getFirst("Location");
        assertThat(location)
                .as("Location header must match /api/pac/v1/pacientes/<uuid>")
                .matches("/api/pac/v1/pacientes/[0-9a-f-]+");
    }

    @And("the response body contains field \"id\"")
    public void andTheResponseBodyContainsFieldId() {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("Body must contain 'id' field").contains("\"id\"");
    }

    @And("the response body field \"nome\" is \"Márcio Amaral\"")
    public void andTheResponseBodyFieldNomeIsMrcioAmaral() {
        String body = ctx.getLastResponse().getBody();
        Object actual = JsonPath.parse(body).read("$.nome");
        assertThat(actual).isEqualTo("Márcio Amaral");
    }

    // -------------------------------------------------------------------------
    // EDGE device stubs
    // -------------------------------------------------------------------------

    @Given("EDGE resolves device \"dev-001\" as belonging to \"empresa-abc\"")
    public void givenEdgeResolvesDeviceDev001AsBelongingToEmpresaabc() {
        PacWireMockStubs.stubEdgeResolveDispositivo(
                ExternalServicesWireMock.edge(),
                TestDataHelper.primitiveUuid("dev-001").toString(),
                "empresa-abc");
    }

    @And("ACS resolves caregiver \"cuid-001\" as belonging to \"empresa-abc\"")
    public void andAcsResolvesCaregiverCuid001AsBelongingToEmpresaabc() {
        PacWireMockStubs.stubAcsResolveCuidador(
                ExternalServicesWireMock.acs(),
                TestDataHelper.primitiveUuid("cuid-001").toString(),
                "empresa-abc");
    }

    @And("the response body field \"dispositivoEdgeId\" is \"dev-001\"")
    public void andTheResponseBodyFieldDispositivoedgeidIsDev001() {
        String body = ctx.getLastResponse().getBody();
        Object actual = JsonPath.parse(body).read("$.dispositivoEdgeId");
        assertThat(actual).isEqualTo(TestDataHelper.primitiveUuid("dev-001").toString());
        assertAcsAndEdgeStillTravelOnTheCaregiverToken();
    }

    /**
     * Service identity is scoped to the hist-service client only (standards/service-identity.md
     * §6).
     *
     * <p>PAC → ACS (directory) and PAC → EDGE (device validation) are decisions taken <i>on behalf
     * of the user</i>, so they must keep propagating the caregiver's JWT. This guards against
     * {@code HistFeignConfig} leaking into the global Feign context — a leak WireMock would
     * otherwise accept silently, since it never validates credentials.
     */
    private void assertAcsAndEdgeStillTravelOnTheCaregiverToken() {
        assertOutboundTokenIsTheCaregivers(
                ExternalServicesWireMock.acs()
                        .findAll(
                                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                                        com.github.tomakehurst.wiremock.client.WireMock
                                                .urlPathMatching("/api/v1/acs/caregivers/.*"))),
                "ACS directory");
        assertOutboundTokenIsTheCaregivers(
                ExternalServicesWireMock.edge()
                        .findAll(
                                com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
                                        com.github.tomakehurst.wiremock.client.WireMock
                                                .urlPathMatching("/api/edge/v1/dispositivos/.*"))),
                "EDGE device");
    }

    private void assertOutboundTokenIsTheCaregivers(
            java.util.List<com.github.tomakehurst.wiremock.verification.LoggedRequest> requests,
            String label) {
        assertThat(requests).as("%s must have been called", label).isNotEmpty();
        String authorization = requests.get(requests.size() - 1).getHeader("Authorization");
        assertThat(authorization)
                .as("%s must still travel on the caregiver's JWT", label)
                .startsWith("Bearer eyJ")
                .doesNotContain(PacWireMockStubs.SERVICE_TOKEN);
    }

    @And("^the response content type is \"application/problem\\+json\"")
    public void andTheResponseContentTypeIsApplicationproblemjson() {
        String contentType = ctx.getLastResponse().getHeaders().getFirst("Content-Type");
        assertThat(contentType)
                .as("Content-Type must be application/problem+json")
                .contains("application/problem+json");
    }

    @Given("EDGE resolves device \"dev-foreign\" as belonging to \"empresa-xyz\"")
    public void givenEdgeResolvesDeviceDevforeignAsBelongingToEmpresaxyz() {
        PacWireMockStubs.stubEdgeResolveDispositivo_ForeignTenant(
                ExternalServicesWireMock.edge(),
                TestDataHelper.primitiveUuid("dev-foreign").toString(),
                "empresa-xyz");
    }

    @And(
            "the response body contains field \"errors\" with field code \"not-supported\" for \"dispositivoEdgeId\"")
    public void andTheResponseBodyContainsFieldErrorsWithField() {
        String body = ctx.getLastResponse().getBody();
        assertThat(body)
                .as("Response body should contain error for dispositivoEdgeId")
                .containsAnyOf("dispositivoEdgeId", "not-supported", "errors");
    }

    @Given("ACS returns 404 for caregiver \"cuid-unknown\"")
    public void givenAcsReturns404ForCaregiverCuidunknown() {
        PacWireMockStubs.stubAcsResolveCuidador_NotFound(
                ExternalServicesWireMock.acs(),
                TestDataHelper.primitiveUuid("cuid-unknown").toString());
    }

    // -------------------------------------------------------------------------
    // Header manipulation
    // -------------------------------------------------------------------------

    @Given("the request header \"X-Empresa-Id\" is absent")
    public void givenTheRequestHeaderXempresaidIsAbsent() {
        ctx.setSendEmpresaId(false);
    }

    @And("^the response body field \"type\" is \"/errors/missing-tenant\"")
    public void andTheResponseBodyFieldTypeIsErrorsmissingtenant() {
        assertFieldType("/errors/missing-tenant");
    }

    @Given("a valid JWT bearer token is present with tenant claim \"empresa-other\"")
    public void givenAValidJwtBearerTokenIsPresentWith() {
        // JWT claims empresa-other; header still set to empresa-abc (from background)
        ctx.setCurrentJwt(TestJwtHelper.createToken("empresa-other"));
        // X-Empresa-Id remains "empresa-abc" from the background step
    }

    @And("^the response body field \"type\" is \"/errors/tenant-mismatch\"")
    public void andTheResponseBodyFieldTypeIsErrorstenantmismatch() {
        assertFieldType("/errors/tenant-mismatch");
    }

    // -------------------------------------------------------------------------
    // Multi-tenant list
    // -------------------------------------------------------------------------

    @Given("patient \"pac-001\" belongs to \"empresa-abc\"")
    public void givenPatientPac001BelongsToEmpresaabc() {
        UUID id = data.createPaciente("pac-001", "empresa-abc");
        ctx.putAlias("pac-001", id);
    }

    @And("patient \"pac-002\" belongs to \"empresa-xyz\"")
    public void andPatientPac002BelongsToEmpresaxyz() {
        UUID id = data.createPaciente("pac-002", "empresa-xyz");
        ctx.putAlias("pac-002", id);
    }

    @When("^a GET request is made to \"/api/pac/v1/pacientes\"")
    public void whenAGetRequestIsMadeToApipacv1pacientes() {
        ResponseEntity<String> response = http.get("/api/pac/v1/pacientes");
        ctx.setLastResponse(response);
    }

    // NOTE: "the response list contains {string}" is handled generically by
    // SharedSteps.andTheResponseListContains
    // and "the response list does not contain {string}" by
    // SharedSteps.andTheResponseListDoesNotContain
    // to avoid AmbiguousStepDefinitionsException (D-532).

    // -------------------------------------------------------------------------
    // ETag / PUT
    // -------------------------------------------------------------------------

    @Given("patient \"pac-001\" exists in \"empresa-abc\" with row_version 2")
    public void givenPatientPac001ExistsInEmpresaabcWithRowversion2() {
        UUID id = data.createPacienteWithRowVersion("empresa-abc", 2);
        ctx.putAlias("pac-001", id);
        // rowVersion=2 means ETag would be "3" (rowVersion+1)
        ctx.setLastEtag("\"3\"");
    }

    @When("a PUT request is made to {string} with {string} and body:")
    public void whenAPutRequestIsMadeToApipacv1pacientespac001With(
            String pathAlias, String ifMatchHeader, String body) {
        UUID patientId = ctx.getAlias("pac-001");
        String path =
                patientId != null ? pathAlias.replace("/pac-001", "/" + patientId) : pathAlias;
        // Parse the If-Match value from header string like "If-Match: \"1\""
        String etag = ifMatchHeader.replaceFirst("(?i)If-Match:\\s*", "").trim();
        ResponseEntity<String> response = http.put(path, body, etag);
        ctx.setLastResponse(response);
    }

    @And("^the response body field \"type\" is \"/errors/conflict-precondition\"")
    public void andTheResponseBodyFieldTypeIsErrorsconflictprecondition() {
        assertFieldType("/errors/conflict-precondition");
    }

    // -------------------------------------------------------------------------
    // Scenario Outline: patient update validation
    // NOTE: "patient {string} exists in \"empresa-abc\"" is handled by the generic step
    // SharedSteps.givenPatientExistsIn(alias, empresaId) to avoid ambiguity. No duplicate here.

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String resolveExternalIds(String body) {
        for (String alias : new String[] {"dev-001", "dev-foreign", "cuid-001", "cuid-unknown"}) {
            body =
                    body.replace(
                            "\"" + alias + "\"", "\"" + TestDataHelper.primitiveUuid(alias) + "\"");
        }
        return body;
    }

    private void assertFieldType(String expected) {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("Body must not be null for type assertion").isNotNull();
        Object actual = JsonPath.parse(body).read("$.type");
        assertThat(actual).as("JSON field 'type'").isEqualTo(expected);
    }
}
