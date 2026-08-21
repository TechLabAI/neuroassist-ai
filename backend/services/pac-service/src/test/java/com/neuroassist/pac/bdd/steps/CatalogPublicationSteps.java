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
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — Catalog Publication (Active Calibrated Catalog). Feature:
 * catalog-publication.feature (PAC-025, P9).
 *
 * <p>Not annotated with @Component — registered exclusively via {@link
 * com.neuroassist.pac.bdd.BddTestConfiguration}.
 */
@RequiredArgsConstructor
public class CatalogPublicationSteps {

    private final ScenarioContext ctx;
    private final HttpTestHelper http;
    private final TestDataHelper data;

    // -------------------------------------------------------------------------
    // Background: catalog scope JWT
    // -------------------------------------------------------------------------

    /**
     * "pac:catalog:read" in the feature file maps to "pac.catalogo-ativo.read" in the security
     * config. We also add all other scopes so lifecycle operations work in the same test.
     */
    @And(
            "a valid JWT bearer token is present with scope \"pac:catalog:read\" and tenant claim \"empresa-abc\"")
    public void andAValidJwtBearerTokenIsPresentWith() {
        ctx.setCurrentEmpresaId("empresa-abc");
        // Give full scopes so all background lifecycle setup steps work
        ctx.setCurrentJwt(TestJwtHelper.createToken("empresa-abc", TestJwtHelper.ALL_SCOPES));
        ctx.setSendEmpresaId(true);
    }

    // -------------------------------------------------------------------------
    // Test data — command states for catalog content invariants
    // -------------------------------------------------------------------------

    @And("command \"cmd-val\" for patient \"pac-001\" exists with estado \"validated\"")
    public void andCommandCmdvalForPatientPac001ExistsWithEstado() {
        UUID patientId = ctx.getAlias("pac-001");
        UUID id = data.createComando(patientId, ctx.getCurrentEmpresaId(), "validated");
        ctx.putAlias("cmd-val", id);
        ctx.setLastEtag("\"1\"");
    }

    // -------------------------------------------------------------------------
    // GET /api/pac/v1/pacientes/{pid}/catalogo-ativo
    // -------------------------------------------------------------------------

    @When("^a GET request is made to \"/api/pac/v1/pacientes/pac-001/catalogo-ativo\"")
    public void whenAGetRequestIsMadeToApipacv1pacientespac001catalogoativo() {
        UUID patientId = ctx.getAlias("pac-001");
        ResponseEntity<String> response =
                http.get("/api/pac/v1/pacientes/" + patientId + "/catalogo-ativo");
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    // -------------------------------------------------------------------------
    // "comandos" list assertions
    // -------------------------------------------------------------------------

    @And("the response \"comandos\" list contains \"cmd-pub\"")
    public void andTheResponseComandosListContainsCmdpub() {
        UUID cmdId = ctx.getAlias("cmd-pub");
        assertThat(ctx.getLastResponse().getBody()).contains(cmdId.toString());
    }

    @And("the response \"comandos\" list does not contain \"cmd-draft\"")
    public void andTheResponseComandosListDoesNotContainCmddraft() {
        UUID cmdId = ctx.getAlias("cmd-draft");
        if (cmdId != null) {
            assertThat(ctx.getLastResponse().getBody()).doesNotContain(cmdId.toString());
        }
    }

    @And("the response \"comandos\" list does not contain \"cmd-val\"")
    public void andTheResponseComandosListDoesNotContainCmdval() {
        UUID cmdId = ctx.getAlias("cmd-val");
        if (cmdId != null) {
            assertThat(ctx.getLastResponse().getBody()).doesNotContain(cmdId.toString());
        }
    }

    @And("the response \"comandos\" list does not contain \"cmd-ret\"")
    public void andTheResponseComandosListDoesNotContainCmdret() {
        UUID cmdId = ctx.getAlias("cmd-ret");
        if (cmdId != null) {
            assertThat(ctx.getLastResponse().getBody()).doesNotContain(cmdId.toString());
        }
    }

    // -------------------------------------------------------------------------
    // Catalog version bumps — publish
    // -------------------------------------------------------------------------

    @Given("patient \"pac-001\" has catalogoVersao 5")
    public void givenPatientPac001HasCatalogoversao5() {
        setupPatientWithCatalogoVersao(5);
    }

    @When(
            "^a POST request is made \\(scope pac:command:publish\\) to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-val:publish\" with matching ETag")
    public void whenAPostRequestIsMadeScopePaccommandpublishTo() {
        PacWireMockStubs.stubHistAvailable(ExternalServicesWireMock.hist());
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-val");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":publish";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    @Then("^a GET to \"/api/pac/v1/pacientes/pac-001/catalogo-ativo\" returns catalogoVersao 6")
    public void thenAGetToApipacv1pacientespac001catalogoativoReturnsCatalogoversao6() {
        assertCatalogoVersao(6);
    }

    // -------------------------------------------------------------------------
    // Catalog version bumps — recalibrate
    // -------------------------------------------------------------------------

    @Given("patient \"pac-001\" has catalogoVersao 7")
    public void givenPatientPac001HasCatalogoversao7() {
        setupPatientWithCatalogoVersao(7);
    }

    @When(
            "^a POST request is made \\(scope pac:command:publish\\) to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:recalibrate\" with matching ETag and body:")
    public void whenAPostRequestIsMadeScopePaccommandpublishTo_2(String body) {
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-pub");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":recalibrate";
        ResponseEntity<String> response = http.postWithEtagAndBody(path, ctx.getLastEtag(), body);
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    @Then("^a GET to \"/api/pac/v1/pacientes/pac-001/catalogo-ativo\" returns catalogoVersao 8")
    public void thenAGetToApipacv1pacientespac001catalogoativoReturnsCatalogoversao8() {
        assertCatalogoVersao(8);
    }

    // -------------------------------------------------------------------------
    // Catalog version bumps — retire
    // -------------------------------------------------------------------------

    @Given("patient \"pac-001\" has catalogoVersao 9")
    public void givenPatientPac001HasCatalogoversao9() {
        setupPatientWithCatalogoVersao(9);
    }

    @When(
            "^a POST request is made \\(scope pac:command:publish\\) to \"/api/pac/v1/pacientes/pac-001/comandos/cmd-pub:retire\" with matching ETag")
    public void whenAPostRequestIsMadeScopePaccommandpublishTo_3() {
        PacWireMockStubs.stubHistAvailable(ExternalServicesWireMock.hist());
        UUID patientId = ctx.getAlias("pac-001");
        UUID cmdId = ctx.getAlias("cmd-pub");
        String path = "/api/pac/v1/pacientes/" + patientId + "/comandos/" + cmdId + ":retire";
        ResponseEntity<String> response = http.postWithEtag(path, ctx.getLastEtag());
        ctx.setLastResponse(response);
        if (response.getHeaders().getETag() != null) {
            ctx.setLastEtag(response.getHeaders().getETag());
        }
    }

    @Then("^a GET to \"/api/pac/v1/pacientes/pac-001/catalogo-ativo\" returns catalogoVersao 10")
    public void thenAGetToApipacv1pacientespac001catalogoativoReturnsCatalogoversao10() {
        assertCatalogoVersao(10);
    }

    // -------------------------------------------------------------------------
    // Catalog payload completeness
    // -------------------------------------------------------------------------

    @Given("command \"cmd-pub\" for patient \"pac-001\" exists with estado \"published\" and:")
    public void givenCommandCmdpubForPatientPac001ExistsWithEstado(
            io.cucumber.datatable.DataTable dataTable) {
        // Parse the DataTable fields and create a command with those field values
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        dataTable.asMaps().forEach(row -> fields.put(row.get("field"), row.get("value")));

        UUID patientId = ctx.getAlias("pac-001");
        String empresaId = ctx.getCurrentEmpresaId();
        String primPrincipal = fields.getOrDefault("primitivoPrincipalId", "gst-001");
        UUID primPrincipalUuid = TestDataHelper.primitiveUuid(primPrincipal);
        int calibracaoVersao = Integer.parseInt(fields.getOrDefault("calibracaoVersao", "0"));
        String urgencia = fields.getOrDefault("urgencia", "rotina");

        UUID id =
                data.createComando(
                        patientId,
                        empresaId,
                        "published",
                        urgencia,
                        calibracaoVersao,
                        primPrincipalUuid,
                        null);
        ctx.putAlias("cmd-pub", id);
        ctx.setLastEtag("\"1\"");
    }

    @And("the response body field \"pacienteId\" is \"pac-001\"")
    public void andTheResponseBodyFieldPacienteidIsPac001() {
        UUID pacienteId = ctx.getAlias("pac-001");
        String body = ctx.getLastResponse().getBody();
        Object actual = JsonPath.parse(body).read("$.pacienteId");
        assertThat(actual.toString()).isEqualTo(pacienteId.toString());
    }

    @And("the response body field \"empresaId\" is \"empresa-abc\"")
    public void andTheResponseBodyFieldEmpresaidIsEmpresaabc() {
        String body = ctx.getLastResponse().getBody();
        Object actual = JsonPath.parse(body).read("$.empresaId");
        assertThat(actual).isEqualTo("empresa-abc");
    }

    @And("the first command in \"comandos\" has field \"calibracaoVersao\" equal to 3")
    public void andTheFirstCommandInComandosHasFieldCalibracaoversao() {
        String body = ctx.getLastResponse().getBody();
        Number actual = JsonPath.parse(body).read("$.comandos[0].calibracaoVersao");
        assertThat(actual.intValue()).isEqualTo(3);
    }

    @And("the first command in \"comandos\" has field \"urgencia\" equal to \"emergencia\"")
    public void andTheFirstCommandInComandosHasFieldUrgencia() {
        String body = ctx.getLastResponse().getBody();
        Object actual = JsonPath.parse(body).read("$.comandos[0].urgencia");
        assertThat(actual).isEqualTo("emergencia");
    }

    // -------------------------------------------------------------------------
    // HTTP Conditional GET (ETag / If-None-Match)
    // -------------------------------------------------------------------------

    @Given("patient \"pac-001\" has catalogoVersao 42")
    public void givenPatientPac001HasCatalogoversao42() {
        setupPatientWithCatalogoVersao(42);
    }

    @When("a GET request is made to {string} with header {string}")
    public void whenAGetRequestIsMadeToWithHeader(String pathAlias, String header) {
        UUID patientId = ctx.getAlias("pac-001");
        String path =
                patientId != null ? pathAlias.replace("/pac-001", "/" + patientId) : pathAlias;
        // Parse header name and value: e.g. "If-None-Match: \"42\""
        String[] parts = header.split(":", 2);
        String headerName = parts[0].trim();
        String headerValue = parts.length > 1 ? parts[1].trim() : "";
        ResponseEntity<String> response = http.getWithHeader(path, headerName, headerValue);
        ctx.setLastResponse(response);
    }

    @And("the response body is empty")
    public void andTheResponseBodyIsEmpty() {
        String body = ctx.getLastResponse().getBody();
        // 304 responses have no body
        assertThat(body == null || body.isEmpty() || body.isBlank())
                .as("Response body should be empty for 304")
                .isTrue();
    }

    @Given("patient \"pac-001\" has catalogoVersao 43")
    public void givenPatientPac001HasCatalogoversao43() {
        setupPatientWithCatalogoVersao(43);
    }

    @And("the response body field \"catalogoVersao\" is 43")
    public void andTheResponseBodyFieldCatalogoversaoIs43() {
        String body = ctx.getLastResponse().getBody();
        Number actual = JsonPath.parse(body).read("$.catalogoVersao");
        assertThat(actual.intValue()).isEqualTo(43);
    }

    // -------------------------------------------------------------------------
    // Tenancy isolation
    // -------------------------------------------------------------------------

    @When("^a GET request is made to \"/api/pac/v1/pacientes/pac-other/catalogo-ativo\"")
    public void whenAGetRequestIsMadeToApipacv1pacientespacothercatalogoativo() {
        UUID patientId = ctx.getAlias("pac-other");
        String path =
                patientId != null
                        ? "/api/pac/v1/pacientes/" + patientId + "/catalogo-ativo"
                        : "/api/pac/v1/pacientes/pac-other/catalogo-ativo";
        ResponseEntity<String> response = http.get(path);
        ctx.setLastResponse(response);
    }

    // -------------------------------------------------------------------------
    // Scope enforcement
    // -------------------------------------------------------------------------

    /**
     * "pac:command:read" in the feature maps to a scope that does NOT include
     * "pac.catalogo-ativo.read" — this triggers 403 from the security layer.
     */
    @Given(
            "a valid JWT bearer token is present with scope \"pac:command:read\" and tenant claim \"empresa-abc\"")
    public void givenAValidJwtBearerTokenIsPresentWith() {
        ctx.setCurrentEmpresaId("empresa-abc");
        // Use a scope that is NOT pac.catalogo-ativo.read → triggers 403 insufficient-scope
        ctx.setCurrentJwt(
                TestJwtHelper.createToken("empresa-abc", TestJwtHelper.COMMAND_READ_ONLY_SCOPES));
        ctx.setSendEmpresaId(true);
    }

    @And("^the response body field \"type\" is \"/errors/insufficient-scope\"")
    public void andTheResponseBodyFieldTypeIsErrorsinsufficientscope() {
        String body = ctx.getLastResponse().getBody();
        assertThat(body).as("Response body must not be null").isNotNull();
        Object actual = JsonPath.parse(body).read("$.type");
        assertThat(actual).isEqualTo("/errors/insufficient-scope");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void setupPatientWithCatalogoVersao(int catalogoVersao) {
        UUID id = data.createPacienteWithCatalogoVersao("pac-001", "empresa-abc", catalogoVersao);
        ctx.putAlias("pac-001", id);
        ctx.setLastEtag("\"1\"");
    }

    private void assertCatalogoVersao(int expected) {
        UUID patientId = ctx.getAlias("pac-001");
        ResponseEntity<String> response =
                http.get("/api/pac/v1/pacientes/" + patientId + "/catalogo-ativo");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        Number actual = JsonPath.parse(response.getBody()).read("$.catalogoVersao");
        assertThat(actual.intValue()).as("catalogoVersao").isEqualTo(expected);
    }
}
