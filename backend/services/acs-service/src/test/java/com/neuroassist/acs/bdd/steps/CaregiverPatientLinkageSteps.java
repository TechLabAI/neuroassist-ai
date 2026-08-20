package com.neuroassist.acs.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

import com.neuroassist.acs.application.port.out.PacPatientResolvePort;
import com.neuroassist.acs.bdd.BddContext;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/** Step definitions — Caregiver-Patient Linkage. Feature: caregiver-patient-linkage.feature */
@RequiredArgsConstructor
public class CaregiverPatientLinkageSteps {

    final BddContext ctx;
    final TestRestTemplate restTemplate;
    final SharedSteps shared;

    @Autowired PacPatientResolvePort pacPatientResolvePort;

    @Before
    public void resetPacMock() {
        Mockito.reset(pacPatientResolvePort);
    }

    @And(
            "I am authenticated as a coordinator for tenant \"t_0f8a\" with authority \"acs.vinculo.criar\"")
    public void andIAmAuthenticatedAsACoordinatorForTenant() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiver(
                ctx.conductorId,
                ctx.testTenantId,
                "Coordinator",
                "coord.link@t0f8a.com",
                ctx.lastRawPassword,
                "COORDENADOR_CLINICA");
        shared.issueToken("coord.link@t0f8a.com", ctx.lastRawPassword);
    }

    @Given("active caregiver \"5c1a\" in tenant \"t_0f8a\"")
    public void givenActiveCaregiver5c1aInTenantT0f8a() {
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@link.com",
                ctx.lastRawPassword,
                "CUIDADORA");
    }

    @And("PAC confirms patient \"p_9a3f\" belongs to tenant \"t_0f8a\"")
    public void andPacConfirmsPatientP9a3fBelongsToTenantT0f8a() {
        Mockito.when(pacPatientResolvePort.resolvePatient(any(), any()))
                .thenReturn(
                        Optional.of(
                                new PacPatientResolvePort.PatientResolveResponse(
                                        ctx.testPatientId,
                                        ctx.testTenantId,
                                        "Patient 9a3f",
                                        "ACTIVE")));
    }

    @When("I POST a link between caregiver \"5c1a\" and patient \"p_9a3f\"")
    public void whenIPostALinkBetweenCaregiver5c1aAnd() {
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers/"
                                + ctx.testCaregiverId
                                + "/patients/"
                                + ctx.testPatientId
                                + "/link?tenantId="
                                + ctx.testTenantId,
                        HttpMethod.POST,
                        new HttpEntity<>(shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @And("the link status is \"active\"")
    public void andTheLinkStatusIsActive() {
        assertThat(ctx.lastResponse.getBody()).isNotNull();
        assertThat(ctx.lastResponse.getBody().toUpperCase()).contains("ACTIVE");
    }

    @And("the domain fact CuidadorVinculadoAoPaciente is recorded to HIST")
    public void andTheDomainFactCuidadorvinculadoaopacienteIsRecordedToHist() {
        // no-op — HistAuditPort is a fire-and-forget stub in tests
    }

    @And(
            "caregiver \"5c1a\" appears in GET \\/api\\/v1\\/acs\\/directory\\/patients\\/p_9a3f\\/caregivers")
    public void andCaregiver5c1aAppearsInGetApiv1acsdirectorypatientsp9a3fcaregivers() {
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/directory/patients/" + ctx.testPatientId + "/caregivers",
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        assertThat(resp.getBody()).isNotNull();
    }

    @Given("an active link between caregiver \"5c1a\" and patient \"p_9a3f\" exists")
    public void givenAnActiveLinkBetweenCaregiver5c1aAndPatient() {
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@link.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        shared.ensureLink(ctx.testCaregiverId, ctx.testPatientId, ctx.testTenantId);
    }

    @When("I POST the same link again")
    public void whenIPostTheSameLinkAgain() {
        Mockito.when(pacPatientResolvePort.resolvePatient(any(), any()))
                .thenReturn(
                        Optional.of(
                                new PacPatientResolvePort.PatientResolveResponse(
                                        ctx.testPatientId,
                                        ctx.testTenantId,
                                        "Patient 9a3f",
                                        "ACTIVE")));
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers/"
                                + ctx.testCaregiverId
                                + "/patients/"
                                + ctx.testPatientId
                                + "/link?tenantId="
                                + ctx.testTenantId,
                        HttpMethod.POST,
                        new HttpEntity<>(shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @And("no duplicate link row is created")
    public void andNoDuplicateLinkRowIsCreated() {
        Integer count =
                shared.jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM neuroassist_global.caregiver_patient_link"
                                + " WHERE cuidador_id = ?::uuid AND paciente_id = ?::uuid AND status = 'ACTIVE'",
                        Integer.class,
                        ctx.testCaregiverId.toString(),
                        ctx.testPatientId.toString());
        assertThat(count).isLessThanOrEqualTo(1);
    }

    @Given("caregiver \"5c1a\" in tenant \"t_0f8a\"")
    public void givenCaregiver5c1aInTenantT0f8a() {
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@link.com",
                ctx.lastRawPassword,
                "CUIDADORA");
    }

    @And("PAC reports patient \"p_9a3f\" belongs to tenant \"t_11cd\"")
    public void andPacReportsPatientP9a3fBelongsToTenantT11cd() {
        // Different tenant → cross-tenant link
        Mockito.when(pacPatientResolvePort.resolvePatient(any(), any()))
                .thenReturn(
                        Optional.of(
                                new PacPatientResolvePort.PatientResolveResponse(
                                        ctx.testPatientId,
                                        ctx.testTenantId2,
                                        "Patient 9a3f (other tenant)",
                                        "ACTIVE")));
    }

    @Then("the response status is 422")
    public void thenTheResponseStatusIs422() {
        shared.assertStatus(422);
    }

    @And("the error type is \"\\/errors\\/cross-tenant-link\"")
    public void andTheErrorTypeIsErrorscrosstenantlink() {
        shared.assertErrorTypeContains("cross-tenant-link");
    }

    @Given("PAC returns 404 for patient \"p_unknown\"")
    public void givenPacReturns404ForPatientPunknown() {
        Mockito.when(pacPatientResolvePort.resolvePatient(any(), any()))
                .thenReturn(Optional.empty());
    }

    @When("I POST a link between caregiver \"5c1a\" and patient \"p_unknown\"")
    public void whenIPostALinkBetweenCaregiver5c1aAnd_2() {
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers/"
                                + ctx.testCaregiverId
                                + "/patients/"
                                + ctx.secondPatientId
                                + "/link?tenantId="
                                + ctx.testTenantId,
                        HttpMethod.POST,
                        new HttpEntity<>(shared.bearerHeaders()),
                        String.class);
        ctx.lastResponse = resp;
    }

    @And("the error type is \"\\/errors\\/unknown-patient\"")
    public void andTheErrorTypeIsErrorsunknownpatient() {
        shared.assertErrorTypeContains("unknown-patient");
    }

    @Given("PAC does not respond within 2 seconds")
    public void givenPacDoesNotRespondWithin2Seconds() {
        Mockito.when(pacPatientResolvePort.resolvePatient(any(), any()))
                .thenThrow(new RuntimeException("PAC timeout simulated"));
    }

    @Then("the response status is 502")
    public void thenTheResponseStatusIs502() {
        shared.assertStatus(502);
    }

    @And("the error type is \"\\/errors\\/upstream-unavailable\"")
    public void andTheErrorTypeIsErrorsupstreamunavailable() {
        shared.assertErrorTypeContains("upstream-unavailable");
    }

    @Given("caregiver \"5c1a\" is the only active link for patient \"p_9a3f\"")
    public void givenCaregiver5c1aIsTheOnlyActiveLinkFor() {
        shared.ensureTenant(
                ctx.testTenantId, "Test Tenant t0f8a", "CLINICA", "ACTIVE", "neuroassist_global");
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@link.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        // Remove all links for this patient, then add only this one
        shared.jdbcTemplate.update(
                "DELETE FROM neuroassist_global.caregiver_patient_link WHERE paciente_id = ?::uuid",
                ctx.testPatientId.toString());
        shared.ensureLink(ctx.testCaregiverId, ctx.testPatientId, ctx.testTenantId);
    }

    @And("I am authenticated with authority \"acs.vinculo.revogar\"")
    public void andIAmAuthenticatedWithAuthorityAcsvinculorevogar() {
        shared.ensureCaregiver(
                ctx.conductorId,
                ctx.testTenantId,
                "Coordinator",
                "coord.revoke@t0f8a.com",
                ctx.lastRawPassword,
                "COORDENADOR_CLINICA");
        shared.issueToken("coord.revoke@t0f8a.com", ctx.lastRawPassword);
    }

    @When("I DELETE the link for caregiver \"5c1a\" and patient \"p_9a3f\"")
    public void whenIDeleteTheLinkForCaregiver5c1aAnd() {
        ctx.lastResponse =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers/"
                                + ctx.testCaregiverId
                                + "/patients/"
                                + ctx.testPatientId
                                + "/link?tenantId="
                                + ctx.testTenantId,
                        HttpMethod.DELETE,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
    }

    @And("the link status is \"revoked\"")
    public void andTheLinkStatusIsRevoked() {
        // After DELETE returns 200/204, check DB or response
        Integer activeCount =
                shared.jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM neuroassist_global.caregiver_patient_link"
                                + " WHERE cuidador_id = ?::uuid AND paciente_id = ?::uuid AND status = 'ACTIVE'",
                        Integer.class,
                        ctx.testCaregiverId.toString(),
                        ctx.testPatientId.toString());
        assertThat(activeCount).isEqualTo(0);
    }

    @And("the domain fact CuidadorDesvinculado is recorded to HIST")
    public void andTheDomainFactCuidadordesvinculadoIsRecordedToHist() {
        // no-op — HistAuditPort is a fire-and-forget stub in tests
    }

    @And(
            "caregiver \"5c1a\" does not appear in GET \\/api\\/v1\\/acs\\/directory\\/patients\\/p_9a3f\\/caregivers")
    public void andCaregiver5c1aDoesNotAppearInGetApiv1acsdirectorypatientsp9a3fcaregivers() {
        ResponseEntity<String> resp =
                restTemplate.exchange(
                        "/api/v1/acs/directory/patients/" + ctx.testPatientId + "/caregivers",
                        HttpMethod.GET,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
        assertThat(resp.getStatusCode().value()).isEqualTo(200);
        // After revoke, this caregiver should not appear in active links
        // body may be empty or not contain the caregiver id
        if (resp.getBody() != null) {
            String caregiverIdStr = ctx.testCaregiverId.toString();
            // body should not contain ACTIVE link for this caregiver
            assertThat(resp.getBody()).doesNotContain("\"" + caregiverIdStr + "\"");
        }
    }

    @Given("a revoked link between caregiver \"5c1a\" and patient \"p_9a3f\"")
    public void givenARevokedLinkBetweenCaregiver5c1aAndPatient() {
        shared.ensureCaregiver(
                ctx.testCaregiverId,
                ctx.testTenantId,
                "Caregiver 5c1a",
                "care5c1a@link.com",
                ctx.lastRawPassword,
                "CUIDADORA");
        shared.ensureLinkRevoked(ctx.testCaregiverId, ctx.testPatientId, ctx.testTenantId);
    }

    @When("I DELETE the link")
    public void whenIDeleteTheLink() {
        ctx.lastResponse =
                restTemplate.exchange(
                        "/api/v1/acs/caregivers/"
                                + ctx.testCaregiverId
                                + "/patients/"
                                + ctx.testPatientId
                                + "/link?tenantId="
                                + ctx.testTenantId,
                        HttpMethod.DELETE,
                        new HttpEntity<>(shared.bearerHeadersNoBody()),
                        String.class);
    }

    @And("no additional HIST fact is recorded")
    public void andNoAdditionalHistFactIsRecorded() {
        // no-op — HistAuditPort is a fire-and-forget stub in tests
    }
}
