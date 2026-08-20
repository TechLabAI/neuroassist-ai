package com.neuroassist.acs.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.acs.bdd.BddContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.When;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Step definitions — Caregiver Management. Feature: caregiver-management.feature */
@RequiredArgsConstructor
public class CaregiverManagementSteps {

    final BddContext ctx;
    final TestRestTemplate restTemplate;
    final SharedSteps shared;

    @And(
            "I am authenticated as a coordinator for tenant \"t_0f8a\" with authority \"acs.cuidador.criar\"")
    public void andIAmAuthenticatedAsACoordinatorForTenant() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiver(
                ctx.conductorId,
                ctx.testTenantId,
                "Coordinator",
                "coord@t0f8a.com",
                ctx.lastRawPassword,
                "COORDENADOR_CLINICA");
        shared.issueToken("coord@t0f8a.com", ctx.lastRawPassword);
    }

    @When("I POST a caregiver with login \"jamilly@familia-amaral\" and papel \"cuidadora\"")
    public void whenIPostACaregiverWithLoginJamillyfamiliaamaralAnd() {
        String body =
                "{\"nome\":\"Jamilly\",\"login\":\"jamilly@familia-amaral\",\"papel\":\"CUIDADORA\",\"senha\":\"Test@1234!\"}";
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers?tenantId=" + ctx.testTenantId,
                        HttpMethod.POST,
                        new HttpEntity<>(body, shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @And("the caregiver status is \"active\"")
    public void andTheCaregiverStatusIsActive() {
        assertThat(ctx.lastResponse.getBody()).isNotNull();
        assertThat(ctx.lastResponse.getBody().toUpperCase()).contains("ACTIVE");
    }

    @And("the response body does not contain \"credential_hash\"")
    public void andTheResponseBodyDoesNotContainCredentialhash() {
        assertThat(ctx.lastResponse.getBody()).doesNotContain("credential_hash");
    }

    @And("the response body does not contain \"senha\"")
    public void andTheResponseBodyDoesNotContainSenha() {
        assertThat(ctx.lastResponse.getBody()).doesNotContain("\"senha\"");
    }

    @Given("an active caregiver with login \"jamilly@familia-amaral\" exists in tenant \"t_0f8a\"")
    public void givenAnActiveCaregiverWithLoginJamillyfamiliaamaralExistsIn() {
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Jamilly",
                "jamilly@familia-amaral",
                ctx.lastRawPassword,
                "CUIDADORA");
    }

    @When("I POST another caregiver with login \"jamilly@familia-amaral\"")
    public void whenIPostAnotherCaregiverWithLoginJamillyfamiliaamaral() {
        String body =
                "{\"nome\":\"Jamilly2\",\"login\":\"jamilly@familia-amaral\",\"papel\":\"CUIDADORA\",\"senha\":\"Test@1234!\"}";
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers?tenantId=" + ctx.testTenantId,
                        HttpMethod.POST,
                        new HttpEntity<>(body, shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @And("the error type is \"\\/errors\\/login-duplicate\"")
    public void andTheErrorTypeIsErrorsloginduplicate() {
        shared.assertErrorTypeContains("login-duplicate");
    }

    @Given("a tenant \"t_1111\" with status \"provisioning\" exists")
    public void givenATenantT1111WithStatusProvisioningExists() {
        shared.ensureTenant(
                ctx.testTenantId2, "Provisioning Tenant", "CLINICA", "PROVISIONING", null);
    }

    @And("I am authenticated as a coordinator for tenant \"t_1111\"")
    public void andIAmAuthenticatedAsACoordinatorForTenant_2() {
        // For a provisioning tenant, try to issue a token — will likely be rejected
        // Insert a conductor for this provisioning tenant, but JWT will still carry tenant context
        shared.ensureCaregiver(
                ctx.conductorId,
                ctx.testTenantId2,
                "Coord t1111",
                "coord@t1111.com",
                ctx.lastRawPassword,
                "COORDENADOR_CLINICA");
        // Use the existing bearer if available, otherwise try to issue (will fail for PROVISIONING)
        if (ctx.bearerToken == null) {
            shared.issueToken("coord@t1111.com", ctx.lastRawPassword);
        }
    }

    @When("I POST a caregiver for tenant \"t_1111\"")
    public void whenIPostACaregiverForTenantT1111() {
        String body =
                "{\"nome\":\"New Caregiver\",\"login\":\"newcare@t1111.com\",\"papel\":\"CUIDADORA\",\"senha\":\"Test@1234!\"}";
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers?tenantId=" + ctx.testTenantId2,
                        HttpMethod.POST,
                        new HttpEntity<>(body, shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @And("the error type is \"\\/errors\\/tenant-not-active\"")
    public void andTheErrorTypeIsErrorstenantnotactive() {
        shared.assertErrorTypeContains("tenant-not-active");
    }

    @Given("an active caregiver \"5c1a\" with no active patient links")
    public void givenAnActiveCaregiver5c1aWithNoActivePatient() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@t0f8a.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        // Remove any existing links
        shared.jdbcTemplate.update(
                "DELETE FROM neuroassist_global.caregiver_patient_link WHERE cuidador_id = ?::uuid",
                ctx.testCaregiverId.toString());
    }

    @And("I am authenticated with authority \"acs.cuidador.desativar\"")
    public void andIAmAuthenticatedWithAuthorityAcscuidadordesativar() {
        shared.ensureCaregiver(
                ctx.conductorId,
                ctx.testTenantId,
                "Coordinator",
                "coord.deact@t0f8a.com",
                ctx.lastRawPassword,
                "COORDENADOR_CLINICA");
        shared.issueToken("coord.deact@t0f8a.com", ctx.lastRawPassword);
    }

    @When("I POST deactivation for caregiver \"5c1a\"")
    public void whenIPostDeactivationForCaregiver5c1a() {
        String body = "{\"status\":\"INACTIVE\"}";
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers/"
                                + ctx.testCaregiverId
                                + "/status?tenantId="
                                + ctx.testTenantId,
                        HttpMethod.PATCH,
                        new HttpEntity<>(body, shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @And("the caregiver status is \"inactive\"")
    public void andTheCaregiverStatusIsInactive() {
        assertThat(ctx.lastResponse.getBody()).isNotNull();
        assertThat(ctx.lastResponse.getBody().toUpperCase()).contains("INACTIVE");
    }

    @Given("a caregiver \"5c1a\" with status \"inactive\"")
    public void givenACaregiver5c1aWithStatusInactive() {
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
    }

    @When("I POST \\/oauth2\\/token with credentials for caregiver \"5c1a\"")
    public void whenIPostOauth2tokenWithCredentialsForCaregiver5c1a() {
        shared.issueToken("care5c1a@t0f8a.com", ctx.lastRawPassword);
    }

    @Given("an active caregiver \"5c1a\" is the only active link for patient \"p_9a3f\"")
    public void givenAnActiveCaregiver5c1aIsTheOnlyActive() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@t0f8a.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        // Remove any other links for this patient
        shared.jdbcTemplate.update(
                "DELETE FROM neuroassist_global.caregiver_patient_link WHERE paciente_id = ?::uuid",
                ctx.testPatientId.toString());
        shared.ensureLink(ctx.testCaregiverId, ctx.testPatientId, ctx.testTenantId);
    }

    @When("I POST a caregiver with login \"test@example.com\" and papel {string}")
    public void whenIPostACaregiverWithLoginTestexamplecomAnd_2(String papel) {
        String body =
                "{\"nome\":\"Test User\",\"login\":\"test@example.com\",\"papel\":\""
                        + papel.toUpperCase()
                        + "\",\"senha\":\"Test@1234!\"}";
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers?tenantId=" + ctx.testTenantId,
                        HttpMethod.POST,
                        new HttpEntity<>(body, shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }
}
