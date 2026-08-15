package com.neuroassist.evnt.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.evnt.bdd.ScenarioContext;
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
        ctx.setPacienteId("pac-1");
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
        assertThat(ctx.getLastResponse().getStatusCode().value())
                .as("Expected HTTP status " + status)
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
                    .as("ProblemDetail field '" + field + "' should end with '" + expectedValue + "'")
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
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        return restTemplate.exchange("/api/v1/reports", HttpMethod.POST, entity, String.class);
    }
}
