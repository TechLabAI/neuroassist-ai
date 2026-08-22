package com.neuroassist.evnt.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.evnt.bdd.BddIds;
import com.neuroassist.evnt.bdd.ScenarioContext;
import com.neuroassist.evnt.bdd.TestJwtDecoderConfig;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@RequiredArgsConstructor
public class SharedSteps {

    private final ScenarioContext ctx;

    private final TestRestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    // -------------------------------------------------------------------------
    // Given — tenant / patient setup
    // -------------------------------------------------------------------------

    @Given("a tenant \"empresa_demo\" with patient \"pac-1\"")
    public void givenATenantEmpresademoWithPatientPac1() {
        ctx.setTenantId("empresa_demo");
        ctx.setPacienteId(BddIds.PACIENTE);
    }

    // -------------------------------------------------------------------------
    // When — POST /api/v1/reports
    // NOTE: {string} avoids Cucumber treating the literal "/" as alternation
    // -------------------------------------------------------------------------

    @When("the edge posts the report to {string}")
    public void whenTheEdgePostsTheReportTo(String url) {
        ResponseEntity<String> response = postReport();
        ctx.setLastResponse(response);
    }

    // -------------------------------------------------------------------------
    // Then — generic status assertion (replaces all literal @Then("status is NNN"))
    // -------------------------------------------------------------------------

    @Then("the response status is {int}")
    public void thenTheResponseStatusIs(int status) {
        ResponseEntity<String> response = ctx.getLastResponse();
        // The body and WWW-Authenticate header are part of the message on purpose: a bare
        // "expected 201 but was 401" says nothing about *why* the request was rejected, and
        // chasing that down cost real time when the resource server was first switched on.
        assertThat(response.getStatusCode().value())
                .as(
                        "Expected HTTP status %d — WWW-Authenticate=%s body=%s",
                        status,
                        response.getHeaders().getFirst("WWW-Authenticate"),
                        response.getBody())
                .isEqualTo(status);
    }

    // -------------------------------------------------------------------------
    // Then — generic ProblemDetail field assertion (avoids "/" in annotation)
    // -------------------------------------------------------------------------

    @And("the ProblemDetail field {string} is {string}")
    public void andTheProblemDetailFieldIs(String field, String expectedValue) {
        try {
            String body = ctx.getLastResponse().getBody();
            assertThat(body).as("Response body must not be null").isNotNull();
            JsonNode json = objectMapper.readTree(body);
            String actual = json.has(field) ? json.get(field).asText() : "";
            assertThat(actual)
                    .as(
                            "ProblemDetail field '"
                                    + field
                                    + "' should end with '"
                                    + expectedValue
                                    + "'")
                    .endsWith(expectedValue);
        } catch (Exception e) {
            throw new AssertionError("Failed to parse ProblemDetail: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    ResponseEntity<String> postReport() {
        Map<String, Object> body = new HashMap<>();
        body.put("reportId", ctx.getReportId());
        body.put("pacienteId", ctx.getPacienteId());
        body.put("commandRef", ctx.getCommandRef());
        body.put("primitives", List.of(Map.of("tipoGestoId", "g01", "confidence", 0.92)));
        body.put("confidence", 0.95);
        body.put("profileVersion", 1);
        body.put("recognizedAt", Instant.now().toString());
        body.put("deviceId", "device-test-01");
        body.put("patientTenant", ctx.getTenantId());
        body.put("deviceTenant", ctx.getDeviceTokenTenant());
        if (ctx.isRawFramePayload()) {
            body.put("rawFramePayload", "base64encodedframedata==");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        // The device authenticates with a device token (edge.device). Its tenant claim — not the
        // body — is what the tenant-mismatch guard compares, which is the whole point of the
        // guard: a device must not be able to assert which tenant it belongs to.
        headers.setBearerAuth(
                TestJwtDecoderConfig.token(
                        ctx.getDeviceTokenTenant(), "device-test-01", "edge.device"));
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        return restTemplate.exchange("/api/v1/reports", HttpMethod.POST, entity, String.class);
    }
}
