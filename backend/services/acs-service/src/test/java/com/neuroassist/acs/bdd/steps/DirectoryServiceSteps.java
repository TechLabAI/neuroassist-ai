package com.neuroassist.acs.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.acs.bdd.BddContext;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;

/**
 * Step definitions — Directory Service. Feature: directory-service.feature
 *
 * <p>DirectoryController was added in P7 with endpoints: GET
 * /api/v1/acs/directory/patients/{id}/caregivers GET /api/v1/acs/directory/tenants/{id} GET
 * /api/v1/acs/directory/caregivers/{id}/patient-scope
 */
@RequiredArgsConstructor
public class DirectoryServiceSteps {

    final BddContext ctx;
    final TestRestTemplate restTemplate;
    final SharedSteps shared;

    @Given("I am authenticated with authority \"acs.directory.read\"")
    public void givenIAmAuthenticatedWithAuthorityAcsdirectoryread() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiver(
                ctx.conductorId,
                ctx.testTenantId,
                "Directory Reader",
                "dir.reader@t0f8a.com",
                ctx.lastRawPassword,
                "COORDENADOR_CLINICA");
        shared.issueToken("dir.reader@t0f8a.com", ctx.lastRawPassword);
    }

    @Given("active caregiver \"5c1a\" is linked to patient \"p_9a3f\" in tenant \"t_0f8a\"")
    public void givenActiveCaregiver5c1aIsLinkedToPatientP9a3f() {
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@dir.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        shared.ensureLink(ctx.testCaregiverId, ctx.testPatientId, ctx.testTenantId);
    }

    @And("active caregiver \"6d2b\" is linked to patient \"p_9a3f\" in tenant \"t_0f8a\"")
    public void andActiveCaregiver6d2bIsLinkedToPatientP9a3f() {
        shared.ensureCaregiver(
                ctx.secondCaregiverId,
                ctx.testTenantId,
                "Caregiver 6d2b",
                "care6d2b@dir.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        shared.ensureLink(ctx.secondCaregiverId, ctx.testPatientId, ctx.testTenantId);
    }

    @When("I GET \\/api\\/v1\\/acs\\/directory\\/patients\\/p_9a3f\\/caregivers")
    public void whenIGetApiv1acsdirectorypatientsp9a3fcaregivers() {
        ctx.lastResponse =
                restTemplate.exchange(
                        "/api/v1/acs/directory/patients/" + ctx.testPatientId + "/caregivers",
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
    }

    @And("the response body contains caregivers \"5c1a\" and \"6d2b\"")
    public void andTheResponseBodyContainsCaregivers5c1aAnd6d2b() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        // Both caregiver IDs should be present in the response
        assertThat(body).contains(ctx.testCaregiverId.toString());
        assertThat(body).contains(ctx.secondCaregiverId.toString());
    }

    @And("the response Cache-Control header includes \"max-age=60\"")
    public void andTheResponseCachecontrolHeaderIncludesMaxage60() {
        var cacheControl = ctx.lastResponse.getHeaders().get("Cache-Control");
        assertThat(cacheControl).isNotNull();
        assertThat(String.join(",", cacheControl)).contains("max-age=60");
    }

    @Given(
            "patient \"p_orphan\" has no active caregiver links and no coordinator in tenant \"t_0f8a\"")
    public void givenPatientPorphanHasNoActiveCaregiverLinksAnd() {
        UUID orphanPatient = ctx.secondPatientId;
        shared.jdbcTemplate.update(
                "DELETE FROM neuroassist_global.caregiver_patient_link WHERE paciente_id = ?::uuid",
                orphanPatient.toString());
    }

    @When("I GET \\/api\\/v1\\/acs\\/directory\\/patients\\/p_orphan\\/caregivers")
    public void whenIGetApiv1acsdirectorypatientsporphancaregivers() {
        ctx.lastResponse =
                restTemplate.exchange(
                        "/api/v1/acs/directory/patients/" + ctx.secondPatientId + "/caregivers",
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
    }

    @And("the response body contains \"responsibleCaregivers\": []")
    public void andTheResponseBodyContainsResponsiblecaregivers() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        // empty caregivers list
        assertThat(body).containsAnyOf("[]", "\"caregivers\":[]", "\"responsibleCaregivers\":[]");
    }

    @And("I revoke the link for caregiver \"5c1a\"")
    public void andIRevokeTheLinkForCaregiver5c1a() {
        shared.jdbcTemplate.update(
                "UPDATE neuroassist_global.caregiver_patient_link SET status = 'REVOKED'"
                        + " WHERE cuidador_id = ?::uuid AND paciente_id = ?::uuid AND status = 'ACTIVE'",
                ctx.testCaregiverId.toString(),
                ctx.testPatientId.toString());
    }

    @And("the response body does NOT contain caregiver \"5c1a\"")
    public void andTheResponseBodyDoesNotContainCaregiver5c1a() {
        assertThat(ctx.lastResponse.getBody()).doesNotContain(ctx.testCaregiverId.toString());
    }

    @And("the response body contains caregiver \"6d2b\"")
    public void andTheResponseBodyContainsCaregiver6d2b() {
        assertThat(ctx.lastResponse.getBody()).contains(ctx.secondCaregiverId.toString());
    }

    @When("I GET \\/api\\/v1\\/acs\\/directory\\/tenants\\/t_0f8a")
    public void whenIGetApiv1acsdirectorytenantst0f8a() {
        ctx.lastResponse =
                restTemplate.exchange(
                        "/api/v1/acs/directory/tenants/" + ctx.testTenantId,
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
    }

    @And("the response body contains \"status\": \"active\"")
    public void andTheResponseBodyContainsStatusActive() {
        assertThat(ctx.lastResponse.getBody()).isNotNull();
        assertThat(ctx.lastResponse.getBody().toUpperCase()).contains("ACTIVE");
    }

    @When("I GET \\/api\\/v1\\/acs\\/directory\\/tenants\\/t_unknown")
    public void whenIGetApiv1acsdirectorytenantstunknown() {
        ctx.lastResponse =
                restTemplate.exchange(
                        "/api/v1/acs/directory/tenants/" + UUID.randomUUID(),
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
    }

    @Then("the response status is 404")
    public void thenTheResponseStatusIs404() {
        shared.assertStatus(404);
    }

    @Given("active caregiver \"5c1a\" is linked to patients \"p_9a3f\" and \"p_1b2c\"")
    public void givenActiveCaregiver5c1aIsLinkedToPatientsP9a3f() {
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@dir.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        shared.ensureLink(ctx.testCaregiverId, ctx.testPatientId, ctx.testTenantId);
        shared.ensureLink(ctx.testCaregiverId, ctx.secondPatientId, ctx.testTenantId);
    }

    @When("I GET \\/api\\/v1\\/acs\\/directory\\/caregivers\\/5c1a\\/patient-scope")
    public void whenIGetApiv1acsdirectorycaregivers5c1apatientscope() {
        ctx.lastResponse =
                restTemplate.exchange(
                        "/api/v1/acs/directory/caregivers/"
                                + ctx.testCaregiverId
                                + "/patient-scope",
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
    }

    @And("the response body contains patients \"p_9a3f\" and \"p_1b2c\"")
    public void andTheResponseBodyContainsPatientsP9a3fAndP1b2c() {
        String body = ctx.lastResponse.getBody();
        assertThat(body).isNotNull();
        assertThat(body).contains(ctx.testPatientId.toString());
        assertThat(body).contains(ctx.secondPatientId.toString());
    }

    @Given("100 active caregivers with links to patient \"p_perf\" in tenant \"t_0f8a\"")
    public void given100ActiveCaregiversWithLinksToPatientPperf() {
        UUID perfPatient = UUID.randomUUID();
        for (int i = 0; i < 100; i++) {
            UUID caregiverId = UUID.randomUUID();
            shared.ensureCaregiver(
                    caregiverId,
                    ctx.testTenantId,
                    "Caregiver perf " + i,
                    "perf" + i + "@t0f8a.com",
                    ctx.lastRawPassword,
                    "CUIDADORA");
            shared.ensureLink(caregiverId, perfPatient, ctx.testTenantId);
        }
        // Store in ctx for the When step
        ctx.testPatientId = perfPatient;
    }

    @When("I GET \\/api\\/v1\\/acs\\/directory\\/patients\\/p_perf\\/caregivers")
    public void whenIGetApiv1acsdirectorypatientspperfcaregivers() {
        long start = System.currentTimeMillis();
        ctx.lastResponse =
                restTemplate.exchange(
                        "/api/v1/acs/directory/patients/" + ctx.testPatientId + "/caregivers",
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
        ctx.lastResponseTimeMs = System.currentTimeMillis() - start;
    }

    @Then("the response time is below 100 milliseconds")
    public void thenTheResponseTimeIsBelow100Milliseconds() {
        assertThat(ctx.lastResponseTimeMs).isLessThan(100);
    }

    @Given("I am NOT authenticated")
    public void givenIAmNotAuthenticated() {
        ctx.bearerToken = null;
    }

    @And("the error type is \"\\/errors\\/unauthenticated\"")
    public void andTheErrorTypeIsErrorsunauthenticated() {
        shared.assertErrorTypeContains("unauthenticated");
    }
}
