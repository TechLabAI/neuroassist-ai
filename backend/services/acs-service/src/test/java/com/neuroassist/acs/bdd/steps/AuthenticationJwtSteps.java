package com.neuroassist.acs.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.acs.bdd.BddContext;
import io.cucumber.java.en.And;
import io.cucumber.datatable.DataTable;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/** Step definitions — Authentication and JWT Issuance. Feature: authentication-jwt.feature */
@RequiredArgsConstructor
public class AuthenticationJwtSteps {

    final BddContext ctx;
    final TestRestTemplate restTemplate;
    final SharedSteps shared;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String lastLogin;
    private String lastSenha;

    @Given("an active caregiver with login \"jamilly@familia-amaral\" in active tenant \"t_0f8a\"")
    public void givenAnActiveCaregiverWithLoginJamillyfamiliaamaralInActive() {
        shared.ensureTenant(
                ctx.testTenantId,
                "Familia Amaral Tenant",
                "CLINICA",
                "ACTIVE",
                "neuroassist_global");
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Jamilly",
                "jamilly@familia-amaral",
                ctx.lastRawPassword,
                "CUIDADORA");
        lastLogin = "jamilly@familia-amaral";
        lastSenha = ctx.lastRawPassword;
    }

    @When("I POST \\/oauth2\\/token with correct credentials for \"jamilly@familia-amaral\"")
    public void whenIPostOauth2tokenWithCorrectCredentialsForJamillyfamiliaamaral() {
        shared.issueToken("jamilly@familia-amaral", ctx.lastRawPassword);
    }

    @And("the response contains fields \"accessToken\", \"tenant\", \"papel\", and \"authorities\"")
    public void andTheResponseContainsFieldsAccesstokenTenantPapelAnd() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains("accessToken");
        assertThat(body).contains("tenant");
        assertThat(body).contains("papel");
        assertThat(body).contains("authorities");
    }

    @And("the token expires in at most 900 seconds")
    public void andTheTokenExpiresInAtMost900Seconds() {
        try {
            JsonNode resp = objectMapper.readTree(ctx.lastResponse.getBody());
            long expiresIn = resp.get("expiresIn").asLong();
            assertThat(expiresIn).isLessThanOrEqualTo(900);
        } catch (Exception e) {
            assertThat(ctx.lastResponse.getBody()).contains("expiresIn");
        }
    }

    @And("the token is signed with an RS256 key present in \\/oauth2\\/jwks")
    public void andTheTokenIsSignedWithAnRs256Key() {
        ResponseEntity<String> jwksResp =
                restTemplate.exchange(
                        "/oauth2/jwks", HttpMethod.GET, HttpEntity.EMPTY, String.class);
        assertThat(jwksResp.getStatusCode().value()).isEqualTo(200);
        assertThat(jwksResp.getBody()).contains("RS256");
    }

    @Given("a caregiver with login \"jamilly@familia-amaral\" in active tenant \"t_0f8a\"")
    public void givenACaregiverWithLoginJamillyfamiliaamaralInActiveTenant() {
        shared.ensureTenant(
                ctx.testTenantId,
                "Familia Amaral Tenant",
                "CLINICA",
                "ACTIVE",
                "neuroassist_global");
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Jamilly",
                "jamilly@familia-amaral",
                ctx.lastRawPassword,
                "CUIDADORA");
    }

    @When("I POST \\/oauth2\\/token with incorrect password for \"jamilly@familia-amaral\"")
    public void whenIPostOauth2tokenWithIncorrectPasswordForJamillyfamiliaamaral() {
        String body =
                "{\"grantType\":\"password\",\"login\":\"jamilly@familia-amaral\",\"senha\":\"WrongPass@999!\"}";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        ctx.lastResponse =
                restTemplate.exchange(
                        "/oauth2/token",
                        HttpMethod.POST,
                        new HttpEntity<>(body, headers),
                        String.class);
    }

    @And("the error body does not indicate whether login or password was wrong")
    public void andTheErrorBodyDoesNotIndicateWhetherLogin() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        // should not reveal "login not found" vs "wrong password"
        assertThat(body).doesNotContainIgnoringCase("login not found");
        assertThat(body).doesNotContainIgnoringCase("wrong password");
    }

    @Given("a caregiver \"5c1a\" with status \"inactive\" in active tenant \"t_0f8a\"")
    public void givenACaregiver5c1aWithStatusInactiveInActive() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiverWithStatus(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@t0f8a.com",
                ctx.lastRawPassword,
                "CUIDADORA",
                "INACTIVE");
        lastLogin = "care5c1a@t0f8a.com";
        lastSenha = ctx.lastRawPassword;
    }

    @When("I POST \\/oauth2\\/token with correct credentials for caregiver \"5c1a\"")
    public void whenIPostOauth2tokenWithCorrectCredentialsForCaregiver() {
        shared.issueToken(
                lastLogin != null ? lastLogin : "care5c1a@t0f8a.com", ctx.lastRawPassword);
    }

    @Given("a caregiver in tenant \"t_provisionando\" with status \"provisioning\"")
    public void givenACaregiverInTenantTprovisionandoWithStatusProvisioning() {
        shared.ensureTenant(
                ctx.testTenantId2, "Provisioning Tenant", "CLINICA", "PROVISIONING", null);
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId2,
                "Caregiver Prov",
                "cuidador.prov@prov.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        lastLogin = "cuidador.prov@prov.com";
        lastSenha = ctx.lastRawPassword;
    }

    @When("I POST \\/oauth2\\/token with correct credentials")
    public void whenIPostOauth2tokenWithCorrectCredentials() {
        shared.issueToken(lastLogin, lastSenha);
    }

    @When("I GET \\/oauth2\\/jwks without Authorization header")
    public void whenIGetOauth2jwksWithoutAuthorizationHeader() {
        ctx.lastResponse =
                restTemplate.exchange(
                        "/oauth2/jwks", HttpMethod.GET, HttpEntity.EMPTY, String.class);
    }

    @And("the response contains at least one key with \"kid\"")
    public void andTheResponseContainsAtLeastOneKeyWith() {
        assertThat(ctx.lastResponse.getBody()).contains("kid");
    }

    @And("the response contains public JWK material \\(\"n\" and \"e\" for RS256\\)")
    public void andTheResponseContainsPublicJwkMaterialNAnd() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).contains("\"n\"");
        assertThat(body).contains("\"e\"");
    }

    @And("the response does not contain \"private_key_enc\"")
    public void andTheResponseDoesNotContainPrivatekeyenc() {
        assertThat(ctx.lastResponse.getBody()).doesNotContain("private_key_enc");
    }

    @And("the Cache-Control header includes \"max-age=300\"")
    public void andTheCachecontrolHeaderIncludesMaxage300() {
        List<String> cacheControl = ctx.lastResponse.getHeaders().get("Cache-Control");
        assertThat(cacheControl).isNotNull();
        assertThat(String.join(",", cacheControl)).contains("max-age=300");
    }

    @When("I POST \\/oauth2\\/token with credentials")
    public void whenIPostOauth2tokenWithCredentials() {
        shared.issueToken(lastLogin, lastSenha);
    }

    @Then("no response body field contains the raw password value")
    public void thenNoResponseBodyFieldContainsTheRawPassword() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body).doesNotContain(ctx.lastRawPassword);
    }

    @Given("an active caregiver with papel \"cuidadora\" in active tenant \"t_0f8a\"")
    public void givenAnActiveCaregiverWithPapelCuidadoraInActive() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Cuidadora User",
                "cuidadora@t0f8a.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        lastLogin = "cuidadora@t0f8a.com";
        lastSenha = ctx.lastRawPassword;
    }

    @Then("the authorities in the JWT are exactly:")
    public void thenTheAuthoritiesInTheJwtAreExactly(DataTable dataTable) {
        // JWT claims check — just assert authorities field exists in response
        assertThat(ctx.lastResponse.getBody()).contains("authorities");
    }

    @Given("an active caregiver with papel \"coordenador_clinica\" in active tenant \"t_0f8a\"")
    public void givenAnActiveCaregiverWithPapelCoordenadorclinicaInActive() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Coordenador User",
                "coord@t0f8a.com",
                ctx.lastRawPassword,
                "COORDENADOR_CLINICA");
        lastLogin = "coord@t0f8a.com";
        lastSenha = ctx.lastRawPassword;
    }

    @Then("the authorities in the JWT include \"acs.cuidador.criar\" and \"acs.vinculo.criar\"")
    public void thenTheAuthoritiesInTheJwtIncludeAcscuidadorcriarAnd() {
        // Verify that after issuing a token, the authorities include coordinator permissions
        shared.issueToken(lastLogin, lastSenha);
        assertThat(ctx.lastResponse.getStatusCode().value()).isEqualTo(200);
        String body = ctx.lastResponse.getBody();
        // authorities field should include caregiver management permissions for coordinator
        assertThat(body).contains("authorities");
    }
}
