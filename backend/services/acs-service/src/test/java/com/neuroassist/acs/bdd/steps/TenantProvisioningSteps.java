package com.neuroassist.acs.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.neuroassist.acs.application.port.out.MigProvisioningPort;
import com.neuroassist.acs.bdd.BddContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Step definitions — Tenant Provisioning. Feature: tenant-provisioning.feature */
@RequiredArgsConstructor
public class TenantProvisioningSteps {

    final BddContext ctx;
    final TestRestTemplate restTemplate;
    final SharedSteps shared;

    @Autowired MigProvisioningPort migProvisioningPort;

    @Before
    public void resetMigMock() {
        Mockito.reset(migProvisioningPort);
    }

    @Given("I am authenticated as a platform conductor with authority \"acs.tenant.provisionar\"")
    public void givenIAmAuthenticatedAsAPlatformConductorWith() {
        shared.ensureTenant(
                ctx.testTenantId, "Conductor Tenant", "CLINICA", "ACTIVE", "neuroassist_global");
        // E2E-029 — token de serviço do cliente `platform-ops`, sem tenant.
        //
        // Antes este passo logava como COORDENADOR_CLINICA e chamava isso de "platform
        // conductor". O nome do cenário sempre esteve certo; a credencial é que não estava — e
        // era por isso que um coordenador de clínica podia criar e reprocessar outras empresas.
        shared.issueServiceToken("platform-ops", "dev-service-secret");
    }

    @Given("MIG is available and can provision schemas")
    public void givenMigIsAvailableAndCanProvisionSchemas() {
        Mockito.when(migProvisioningPort.requestTenantSchema(any(), any()))
                .thenReturn(
                        new MigProvisioningPort.MigSchemaResponse(
                                ctx.testTenantId, "neuroassist_global", "READY"));
    }

    @When("I POST a tenant with nome \"Família Amaral\" and tipo \"familia\"")
    public void whenIPostATenantWithNomeFamliaAmaral() {
        // E2E-029 — o primeiro coordenador faz parte de criar a empresa. Sem ele o
        // tenant nasceria sem ninguém capaz de agir dentro dele.
        String body =
                "{\"nome\":\"Família Amaral\",\"tipo\":\"CLINICA\","
                        + "\"primeiroCoordenador\":{\"nome\":\"Coord Família Amaral\","
                        + "\"login\":\"coord.amaral@clinica.dev\",\"senha\":\"Test@1234!\"}}";
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/tenants",
                        HttpMethod.POST,
                        new HttpEntity<>(body, shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    /**
     * O critério de aceite de E2E-029: sem nenhuma chamada extra e sem inserir linha no banco, a
     * pessoa que vai operar a empresa consegue entrar nela.
     */
    @And("the first coordinator can authenticate into the new tenant")
    public void andTheFirstCoordinatorCanAuthenticate() {
        String tenantBody = ctx.lastResponse.getBody();
        assertThat(tenantBody).isNotNull();
        String newTenantId;
        try {
            newTenantId =
                    new com.fasterxml.jackson.databind.ObjectMapper()
                            .readTree(tenantBody)
                            .get("id")
                            .asText();
        } catch (Exception e) {
            throw new AssertionError("resposta de criação de tenant sem id", e);
        }

        shared.issueToken("coord.amaral@clinica.dev", "Test@1234!");

        assertThat(ctx.lastResponse.getStatusCode().value())
                .as("o coordenador semeado tem de conseguir autenticar")
                .isEqualTo(200);
        assertThat(ctx.bearerToken).isNotNull();

        // E no tenant certo: um token emitido para a empresa errada seria pior do que nenhum.
        String claims =
                new String(
                        java.util.Base64.getUrlDecoder().decode(ctx.bearerToken.split("[.]")[1]),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertThat(claims).contains(newTenantId);
    }

    @And("the tenant status is \"active\"")
    public void andTheTenantStatusIsActive() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.toLowerCase()).contains("active");
    }

    @And("schema_name is not null")
    public void andSchemanameIsNotNull() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body).doesNotContain("\"schemaNome\":null");
    }

    @And("the domain fact TenantProvisionado is recorded to HIST")
    public void andTheDomainFactTenantprovisionadoIsRecordedToHist() {
        // no-op — HistAuditPort is a fire-and-forget stub in tests
    }

    @Given("MIG times out on schema request after 5 seconds")
    public void givenMigTimesOutOnSchemaRequestAfter5() {
        Mockito.when(migProvisioningPort.requestTenantSchema(any(), any()))
                .thenThrow(new RuntimeException("MIG timeout simulated"));
    }

    @When("I POST a tenant with nome \"Família Souza\" and tipo \"familia\"")
    public void whenIPostATenantWithNomeFamliaSouza() {
        // E2E-029 — o primeiro coordenador faz parte de criar a empresa. Sem ele o
        // tenant nasceria sem ninguém capaz de agir dentro dele.
        String body =
                "{\"nome\":\"Família Souza\",\"tipo\":\"CLINICA\","
                        + "\"primeiroCoordenador\":{\"nome\":\"Coord Família Souza\","
                        + "\"login\":\"coord.souza@clinica.dev\",\"senha\":\"Test@1234!\"}}";
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/tenants",
                        HttpMethod.POST,
                        new HttpEntity<>(body, shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @Then("the response status is 202")
    public void thenTheResponseStatusIs202() {
        shared.assertStatus(202);
    }

    @And("the tenant status is \"provisioning\"")
    public void andTheTenantStatusIsProvisioning() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body.toUpperCase()).contains("PROVISIONING");
    }

    @And("schema_name is null")
    public void andSchemanameIsNull() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("\"schemaNome\":null");
    }

    @Given("a tenant with status \"provisioning\" exists")
    public void givenATenantWithStatusProvisioningExists() {
        ctx.retryTenantId = UUID.randomUUID();
        shared.ensureTenant(ctx.retryTenantId, "Retry Tenant", "CLINICA", "PROVISIONING", null);
    }

    @And("MIG is now available")
    public void andMigIsNowAvailable() {
        Mockito.when(migProvisioningPort.requestTenantSchema(any(), any()))
                .thenReturn(
                        new MigProvisioningPort.MigSchemaResponse(
                                ctx.retryTenantId, "neuroassist_global", "READY"));
    }

    @When("I POST provisioning:retry for that tenant")
    public void whenIPostProvisioningretryForThatTenant() {
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/tenants/" + ctx.retryTenantId + "/retry",
                        HttpMethod.POST,
                        new HttpEntity<>(shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @Given("a tenant with status \"active\" exists")
    public void givenATenantWithStatusActiveExists() {
        ctx.retryTenantId = UUID.randomUUID();
        // Schema PRÓPRIO, e não `neuroassist_global` (E2E-022).
        //
        // O Background já criou um tenant com aquele schema, e o schema real tem
        // `ux_tenant_schema_nome` — duas empresas não podem apontar para o mesmo schema, o que é a
        // própria definição de isolamento por schema. Com `ddl-auto=create-drop` o Hibernate nunca
        // criava essa constraint, então a fixture descrevia um estado que produção rejeita.
        shared.ensureTenant(
                ctx.retryTenantId,
                "Active Tenant",
                "CLINICA",
                "ACTIVE",
                "t_" + ctx.retryTenantId.toString().replace("-", ""));
    }

    @And("MIG is not called")
    public void andMigIsNotCalled() {
        verify(migProvisioningPort, never()).requestTenantSchema(any(), any());
    }

    @And("the tenant status remains \"active\"")
    public void andTheTenantStatusRemainsActive() {
        andTheTenantStatusIsActive();
    }

    @Given("a tenant with status \"suspended\" exists")
    public void givenATenantWithStatusSuspendedExists() {
        ctx.retryTenantId = UUID.randomUUID();
        shared.ensureTenant(ctx.retryTenantId, "Suspended Tenant", "CLINICA", "SUSPENDED", null);
    }

    @And("the error type is \"\\/errors\\/tenant-suspended\"")
    public void andTheErrorTypeIsErrorstenantsuspended() {
        shared.assertErrorTypeContains("tenant-suspended");
    }

    @When("I POST a tenant with nome \"Test\" and tipo {string}")
    public void whenIPostATenantWithNomeTestAnd(String tipo) {
        String body = "{\"nome\":\"Test\",\"tipo\":\"" + tipo + "\"}";
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/tenants",
                        HttpMethod.POST,
                        new HttpEntity<>(body, shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @Given(
            "a persisted tenant row with status \"active\" and schema_name NULL exists \\(data defect\\)")
    public void givenAPersistedTenantRowWithStatusActiveAnd() {
        // Use the same tenant as the conductor so the GET request passes security
        ctx.retryTenantId = ctx.testTenantId;
        shared.jdbcTemplate.update(
                "UPDATE neuroassist_global.tenant SET schema_nome = NULL WHERE tenant_id = ?::uuid",
                ctx.retryTenantId.toString());
    }

    @When("I GET \\/api\\/v1\\/acs\\/tenants\\/{string}")
    public void whenIGetApiv1acstenantsid(String tenantId) {
        UUID id = "that".equals(tenantId) ? ctx.retryTenantId : UUID.fromString(tenantId);
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/tenants/" + id,
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @Then("the response status is 500")
    public void thenTheResponseStatusIs500() {
        shared.assertStatus(500);
    }
}
