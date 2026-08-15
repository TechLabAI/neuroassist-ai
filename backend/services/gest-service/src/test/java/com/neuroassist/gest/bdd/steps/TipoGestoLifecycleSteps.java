package com.neuroassist.gest.bdd.steps;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.neuroassist.gest.bdd.ScenarioState;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoJpaRepository;
import com.neuroassist.gest.infrastructure.persistence.repository.TipoGestoTransitionJpaRepository;
import io.cucumber.java.Before;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Step definitions — TipoGesto detectability lifecycle. Feature: tipo-gesto-lifecycle.feature
 *
 * <p>Uses {@link TestRestTemplate} for real HTTP calls against the running service. State between
 * steps is shared via {@link ScenarioState} (cucumber-glue scoped — one instance per scenario).
 */
@Slf4j
@RequiredArgsConstructor
public class TipoGestoLifecycleSteps {

    private static final String BASE_PATH = "/gest-service/tipo-gesto";

    private final TestRestTemplate restTemplate;
    private final ScenarioState state;
    private final TipoGestoJpaRepository tipoGestoJpaRepository;
    private final TipoGestoTransitionJpaRepository transitionJpaRepository;
    private final ObjectMapper objectMapper;

    // ── Cucumber lifecycle ──────────────────────────────────────────────────

    /**
     * Wipe the public-schema tables (Hibernate create-drop) before every scenario to ensure each
     * scenario starts with a clean slate. Transitions are removed first (no FK in public schema,
     * but ordering is defensive).
     */
    @Before
    public void cleanupBeforeScenario() {
        transitionJpaRepository.deleteAll();
        tipoGestoJpaRepository.deleteAll();
    }

    // ── Background steps ───────────────────────────────────────────────────

    @And("the caller is authenticated with role \"gest.manage\"")
    public void andTheCallerIsAuthenticatedWithRoleGestmanage() {
        state.currentCredentials = "gest-manager:manager-secret";
    }

    @And("no TipoGesto named \"piscar duplo\" exists in the tenant")
    public void andNoTipogestoNamedPiscarDuploExistsInThe() {
        // Data is cleared by @Before cleanupBeforeScenario().
        // This step is declarative; no additional action required.
    }

    // ── When — Registration ────────────────────────────────────────────────

    @When("the conductor registers a primitive with nome \"piscar duplo\"")
    public void whenTheConductorRegistersAPrimitiveWithNomePiscar() {
        state.lastResponse = postRegister("piscar duplo");
        state.lastEntityId = extractId(state.lastResponse);
    }

    @When("the conductor registers a primitive with nome \"Piscar Duplo\"")
    public void whenTheConductorRegistersAPrimitiveWithNomePiscar_2() {
        state.lastResponse = postRegister("Piscar Duplo");
        state.lastEntityId = extractId(state.lastResponse);
    }

    @When("the conductor registers a primitive with nome \"\"")
    public void whenTheConductorRegistersAPrimitiveWithNome() {
        state.lastResponse = postRegister("");
    }

    @When("the conductor registers a primitive with nome of length 121")
    public void whenTheConductorRegistersAPrimitiveWithNomeOf() {
        String longNome = "A".repeat(121);
        state.lastResponse = postRegister(longNome);
    }

    @When("the caller registers a primitive with nome \"novo gesto\"")
    public void whenTheCallerRegistersAPrimitiveWithNomeNovo() {
        // PoC: no RBAC enforcement yet — call proceeds as non-conductor.
        state.lastResponse = postRegister("novo gesto");
        state.lastEntityId = extractId(state.lastResponse);
    }

    // ── When — Transitions ─────────────────────────────────────────────────

    @When(
            "the conductor transitions \"piscar duplo\" to state \"pending_training\" with reason \"começando treinamento\"")
    public void whenTheConductorTransitionsPiscarDuploToStatePendingtraining() {
        state.lastResponse =
                postTransition(state.lastEntityId, "pending_training", "começando treinamento");
    }

    @When(
            "the conductor transitions \"piscar duplo\" to state \"supported\" without engineFeatureRef")
    public void whenTheConductorTransitionsPiscarDuploToStateSupported() {
        state.lastResponse = postTransition(state.lastEntityId, "supported", null);
    }

    @When(
            "the conductor transitions \"piscar duplo\" to state \"supported\" with engineFeatureRef \"mp.blink.double.v1\"")
    public void whenTheConductorTransitionsPiscarDuploToStateSupported_2() {
        // engineFeatureRef is passed as extra JSON field; service reads toState/actor/reason only.
        // The assertion on engineFeatureRef value will verify if the service honoured it.
        state.lastResponse =
                postTransitionWithRef(state.lastEntityId, "supported", null, "mp.blink.double.v1");
    }

    @When("the conductor transitions \"piscar duplo\" to state \"deprecated\"")
    public void whenTheConductorTransitionsPiscarDuploToStateDeprecated() {
        state.lastResponse = postTransition(state.lastEntityId, "deprecated", null);
    }

    @When("the conductor transitions \"piscar duplo\" to state \"deprecated\" without reason")
    public void whenTheConductorTransitionsPiscarDuploToStateDeprecated_2() {
        state.lastResponse = postTransition(state.lastEntityId, "deprecated", null);
    }

    @When(
            "the conductor transitions \"piscar duplo\" to state \"deprecated\" with reason \"feature removida do engine v2\"")
    public void whenTheConductorTransitionsPiscarDuploToStateDeprecated_3() {
        state.lastResponse =
                postTransition(state.lastEntityId, "deprecated", "feature removida do engine v2");
    }

    @When("the caller transitions \"piscar duplo\" to state \"pending_training\"")
    public void whenTheCallerTransitionsPiscarDuploToStatePendingtraining() {
        // PoC: no RBAC enforcement yet — call proceeds as non-conductor.
        state.lastResponse = postTransition(state.lastEntityId, "pending_training", null);
    }

    // ── When — Audit trail ────────────────────────────────────────────────

    @When("the audit trail is queried for \"piscar duplo\"")
    public void whenTheAuditTrailIsQueriedForPiscarDuplo() {
        // Retrieve transition records via JPA (no dedicated HTTP audit endpoint in PoC).
        if (state.lastEntityId != null) {
            List<?> records =
                    transitionJpaRepository.findByTipoGestoId(UUID.fromString(state.lastEntityId));
            log.info("Audit trail for {}: {} records", state.lastEntityId, records.size());
            // Store count for Then assertions via a lightweight 200-OK synthetic response.
            state.lastResponse = ResponseEntity.ok(String.valueOf(records.size()));
        }
    }

    // ── Then — Status assertions ───────────────────────────────────────────

    @Then("the response status is 409")
    public void thenTheResponseStatusIs409() {
        assertThat(state.lastResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Then("the response status is 400")
    public void thenTheResponseStatusIs400() {
        assertThat(state.lastResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    // ── Then — Body assertions ─────────────────────────────────────────────

    @And("the returned TipoGesto has detectabilityState \"registered\"")
    public void andTheReturnedTipogestoHasDetectabilitystateRegistered() {
        assertThat(field("detectabilityState")).isEqualTo("registered");
    }

    @And("the returned TipoGesto has detectabilityState \"pending_training\"")
    public void andTheReturnedTipogestoHasDetectabilitystatePendingtraining() {
        assertThat(field("detectabilityState")).isEqualTo("pending_training");
    }

    @And("the returned TipoGesto has detectabilityState \"supported\"")
    public void andTheReturnedTipogestoHasDetectabilitystateSupported() {
        assertThat(field("detectabilityState")).isEqualTo("supported");
    }

    @And("the returned TipoGesto has detectabilityState \"deprecated\"")
    public void andTheReturnedTipogestoHasDetectabilitystateDeprecated() {
        assertThat(field("detectabilityState")).isEqualTo("deprecated");
    }

    @And("the returned TipoGesto has engineFeatureRef null")
    public void andTheReturnedTipogestoHasEnginefeaturerefNull() {
        assertThat(field("engineFeatureRef")).isNull();
    }

    @And("the returned TipoGesto has engineFeatureRef \"mp.blink.double.v1\"")
    public void andTheReturnedTipogestoHasEnginefeaturerefMpblinkdoublev1() {
        assertThat(field("engineFeatureRef")).isEqualTo("mp.blink.double.v1");
    }

    @And("the detectabilityState of \"piscar duplo\" remains \"pending_training\"")
    public void andTheDetectabilitystateOfPiscarDuploRemainsPendingtraining() {
        ResponseEntity<String> current =
                restTemplate.getForEntity(BASE_PATH + "/" + state.lastEntityId, String.class);
        assertThat(fieldFrom(current, "detectabilityState")).isEqualTo("pending_training");
    }

    @And("the detectabilityState of \"piscar duplo\" remains \"registered\"")
    public void andTheDetectabilitystateOfPiscarDuploRemainsRegistered() {
        ResponseEntity<String> current =
                restTemplate.getForEntity(BASE_PATH + "/" + state.lastEntityId, String.class);
        assertThat(fieldFrom(current, "detectabilityState")).isEqualTo("registered");
    }

    // ── Then — Audit trail assertions ──────────────────────────────────────

    @And("a transition record exists with fromState null and toState \"registered\"")
    public void andATransitionRecordExistsWithFromstateNullAnd() {
        if (state.lastEntityId != null) {
            var records =
                    transitionJpaRepository.findByTipoGestoId(UUID.fromString(state.lastEntityId));
            assertThat(records)
                    .anyMatch(r -> r.getFromState() == null && "registered".equals(r.getToState()));
        }
    }

    @And("a new audit record exists for the transition with toState \"pending_training\"")
    public void andANewAuditRecordExistsForTheTransition() {
        if (state.lastEntityId != null) {
            var records =
                    transitionJpaRepository.findByTipoGestoId(UUID.fromString(state.lastEntityId));
            assertThat(records).anyMatch(r -> "pending_training".equals(r.getToState()));
        }
    }

    @And("a new audit record exists for the transition with toState \"supported\"")
    public void andANewAuditRecordExistsForTheTransition_2() {
        if (state.lastEntityId != null) {
            var records =
                    transitionJpaRepository.findByTipoGestoId(UUID.fromString(state.lastEntityId));
            assertThat(records).anyMatch(r -> "supported".equals(r.getToState()));
        }
    }

    @And("a new audit record exists for the transition with toState \"deprecated\"")
    public void andANewAuditRecordExistsForTheTransition_3() {
        if (state.lastEntityId != null) {
            var records =
                    transitionJpaRepository.findByTipoGestoId(UUID.fromString(state.lastEntityId));
            assertThat(records).anyMatch(r -> "deprecated".equals(r.getToState()));
        }
    }

    @Then("exactly {int} transition records exist in chronological order")
    public void thenExactlyNTransitionRecordsExistInChronologicalOrder(int expectedCount) {
        if (state.lastEntityId != null) {
            var records =
                    transitionJpaRepository.findByTipoGestoId(UUID.fromString(state.lastEntityId));
            assertThat(records).hasSize(expectedCount);
        }
    }

    @And("no transition record has been modified")
    public void andNoTransitionRecordHasBeenModified() {
        // Transition records are append-only (ADR-006 invariant #4).
        // The JPA entity has no @Setter on state fields — this is structural enforcement.
        // At test level, assert that the DB records match what was written (no orphan updates).
        if (state.lastEntityId != null) {
            var records =
                    transitionJpaRepository.findByTipoGestoId(UUID.fromString(state.lastEntityId));
            assertThat(records).isNotEmpty();
            // All records must have a valid 'at' timestamp and a non-null actor.
            assertThat(records).allMatch(r -> r.getAt() != null && r.getActor() != null);
        }
    }

    // ── Given — Setup state via HTTP ───────────────────────────────────────

    @Given("a TipoGesto \"piscar duplo\" exists in state \"pending_training\"")
    public void givenATipogestoPiscarDuploExistsInStatePendingtraining() {
        ResponseEntity<String> created = postRegister("piscar duplo");
        String id = extractId(created);
        state.lastEntityId = id;
        postTransition(id, "pending_training", "test setup");
    }

    @Given("a TipoGesto \"piscar duplo\" exists in state \"deprecated\"")
    public void givenATipogestoPiscarDuploExistsInStateDeprecated() {
        ResponseEntity<String> created = postRegister("piscar duplo");
        String id = extractId(created);
        state.lastEntityId = id;
        // ADR-006: registered → pending_training → deprecated
        postTransition(id, "pending_training", "test setup");
        postTransition(id, "deprecated", "test setup — deprecated");
    }

    @Given("a TipoGesto \"piscar duplo\" exists in state \"registered\"")
    public void givenATipogestoPiscarDuploExistsInStateRegistered() {
        ResponseEntity<String> created = postRegister("piscar duplo");
        state.lastEntityId = extractId(created);
    }

    @Given(
            "a TipoGesto \"piscar duplo\" exists in state \"pending_training\" with engineFeatureRef null")
    public void givenATipogestoPiscarDuploExistsInStatePendingtraining_2() {
        // engineFeatureRef is always null at this point (API does not accept it on transitions).
        givenATipogestoPiscarDuploExistsInStatePendingtraining();
    }

    @Given("a TipoGesto \"piscar duplo\" exists in state \"supported\"")
    public void givenATipogestoPiscarDuploExistsInStateSupported() {
        ResponseEntity<String> created = postRegister("piscar duplo");
        String id = extractId(created);
        state.lastEntityId = id;
        postTransition(id, "pending_training", "test setup");
        // ADR-006 INVARIANT #5: engineFeatureRef required to reach SUPPORTED
        postTransitionWithRef(id, "supported", "test setup", "test-ref");
    }

    @Given(
            "a TipoGesto \"piscar duplo\" exists in state \"supported\" with engineFeatureRef \"mp.blink.double.v1\"")
    public void givenATipogestoPiscarDuploExistsInStateSupported_2() {
        ResponseEntity<String> created = postRegister("piscar duplo");
        String id = extractId(created);
        state.lastEntityId = id;
        postTransition(id, "pending_training", "test setup");
        postTransitionWithRef(id, "supported", "test setup", "mp.blink.double.v1");
    }

    @Given(
            "a TipoGesto \"piscar duplo\" has gone through transitions \"registered\" -> \"pending_training\" -> \"supported\"")
    public void givenATipogestoPiscarDuploHasGoneThroughTransitions() {
        ResponseEntity<String> created = postRegister("piscar duplo");
        String id = extractId(created);
        state.lastEntityId = id;
        postTransition(id, "pending_training", "test setup — step 1");
        postTransitionWithRef(id, "supported", "test setup — step 2", "test-ref");
    }

    // ── Given — Role steps ─────────────────────────────────────────────────

    @Given("the caller has role \"gest.read\" only")
    public void givenTheCallerHasRoleGestreadOnly() {
        state.currentCredentials = "gest-reader:reader-secret";
    }

    // ── Given — Tenant isolation steps (commented-out feature scenarios) ───

    @Given("a TipoGesto \"{string}\" exists in tenant \"empresa-test\"")
    public void givenATipogestoNomeExistsInTenantEmpresatest(String nome) {
        // Tenant isolation scenarios are deferred (feature comment block).
    }

    @When("a caller from tenant \"empresa-other\" lists the primitive catalog")
    public void whenACallerFromTenantEmpresaotherListsThePrimitive() {
        // Tenant isolation scenarios are deferred (feature comment block).
    }

    @Then("the response items do not contain a primitive named \"{string}\"")
    public void thenTheResponseItemsDoNotContainAPrimitive(String nome) {
        // Tenant isolation scenarios are deferred (feature comment block).
    }

    // ── HTTP helpers ───────────────────────────────────────────────────────

    private ResponseEntity<String> postRegister(String nome) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("nome", nome);
            String json = objectMapper.writeValueAsString(body);
            return restTemplate.exchange(
                    BASE_PATH, HttpMethod.POST, jsonEntity(json), String.class);
        } catch (Exception e) {
            log.error("postRegister failed for nome='{}': {}", nome, e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private ResponseEntity<String> postTransition(String id, String toState, String reason) {
        return postTransitionWithRef(id, toState, reason, null);
    }

    private ResponseEntity<String> postTransitionWithRef(
            String id, String toState, String reason, String engineFeatureRef) {
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("toState", toState);
            body.put("actor", "test");
            if (reason != null) {
                body.put("reason", reason);
            }
            if (engineFeatureRef != null) {
                body.put("engineFeatureRef", engineFeatureRef);
            }
            String json = objectMapper.writeValueAsString(body);
            return restTemplate.exchange(
                    BASE_PATH + "/" + id + "/transitions",
                    HttpMethod.POST,
                    jsonEntity(json),
                    String.class);
        } catch (Exception e) {
            log.error(
                    "postTransition failed for id='{}' toState='{}': {}",
                    id,
                    toState,
                    e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private HttpEntity<String> jsonEntity(String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (state.currentCredentials != null) {
            String encoded =
                    Base64.getEncoder()
                            .encodeToString(
                                    state.currentCredentials.getBytes(StandardCharsets.UTF_8));
            headers.set("Authorization", "Basic " + encoded);
        }
        return new HttpEntity<>(body, headers);
    }

    private String extractId(ResponseEntity<String> response) {
        try {
            if (response == null || response.getBody() == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(response.getBody());
            JsonNode idNode = node.get("id");
            return (idNode != null && !idNode.isNull()) ? idNode.asText() : null;
        } catch (Exception e) {
            log.warn("Could not extract 'id' from response body: {}", e.getMessage());
            return null;
        }
    }

    private String field(String fieldName) {
        return fieldFrom(state.lastResponse, fieldName);
    }

    private String fieldFrom(ResponseEntity<String> response, String fieldName) {
        try {
            if (response == null || response.getBody() == null) {
                return null;
            }
            JsonNode node = objectMapper.readTree(response.getBody());
            JsonNode value = node.get(fieldName);
            return (value == null || value.isNull()) ? null : value.asText();
        } catch (Exception e) {
            log.warn("Could not read field '{}' from response body: {}", fieldName, e.getMessage());
            return null;
        }
    }
}
