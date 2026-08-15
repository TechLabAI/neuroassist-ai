package com.neuroassist.evnt.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.evnt.bdd.ScenarioContext;
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

/**
 * Shared step definitions — steps common to two or more feature files (P8: EVNT-022/023/024).
 *
 * <p>Uses {@link ScenarioContext} to share HTTP state between Given/When/Then phases.
 */
@RequiredArgsConstructor
public class SharedSteps {

    private final ScenarioContext ctx;

    private final TestRestTemplate restTemplate;

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
    // -------------------------------------------------------------------------

    @When("the edge posts the report to \"/api/v1/reports\"")
    public void whenTheEdgePostsTheReportToApiv1reports() {
        ResponseEntity<String> response = postReport();
        ctx.setLastResponse(response);
    }

    // -------------------------------------------------------------------------
    // Then — status assertions
    // -------------------------------------------------------------------------

    @Then("the response status is 200")
    public void thenTheResponseStatusIs200() {
        assertThat(ctx.getLastResponse().getStatusCode().value()).isEqualTo(200);
    }

    @Then("the response status is 201")
    public void thenTheResponseStatusIs201() {
        assertThat(ctx.getLastResponse().getStatusCode().value()).isEqualTo(201);
    }

    @Then("the response status is 422")
    public void thenTheResponseStatusIs422() {
        assertThat(ctx.getLastResponse().getStatusCode().value()).isEqualTo(422);
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
        if (ctx.isRawFramePayload()) {
            body.put("rawFramePayload", "base64encodedframedata==");
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        return restTemplate.exchange("/api/v1/reports", HttpMethod.POST, entity, String.class);
    }
}
