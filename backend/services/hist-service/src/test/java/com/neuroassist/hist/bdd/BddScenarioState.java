package com.neuroassist.hist.bdd;

import io.cucumber.spring.ScenarioScope;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Per-scenario state bag for BDD step definitions. Scoped to {@code cucumber-glue} so each
 * Cucumber scenario starts with a fresh instance. Shared via Spring injection across all step
 * definition classes.
 *
 * <p>Naming convention for symbolic entity identifiers (e.g. "pac-1", "cg-1"):
 * UUID is derived deterministically from the name so that the same symbolic name always maps to
 * the same UUID within a test run.
 */
@Component
@ScenarioScope
public class BddScenarioState {

    /** Tenant name used in JWT claims. "familia-silva" maps to the test tenant. */
    private String tenantId = "t_emp_test";

    /** Current bearer token (set by background/given steps). */
    private String currentToken;

    /** Last HTTP response from the server. */
    private ResponseEntity<String> lastResponse;

    /** The current audit fact request payload (built across multiple Given/And steps). */
    private Map<String, Object> pendingFactRequest;

    /** factId (UUID string) of the first submission, for idempotency comparison. */
    private UUID firstSubmittedFactId;

    /** Record id returned by the first submission, for idempotency comparison. */
    private UUID firstRecordId;

    /** Responses from concurrent submissions. */
    private final List<ResponseEntity<String>> concurrentResponses = new ArrayList<>();

    /** Hashes collected before/after verify to check immutability. */
    private final List<String> recordHashesBeforeVerify = new ArrayList<>();

    /** recordHash of the last ingested record (for prevHash chain assertions). */
    private String lastIngestedRecordHash;

    /** internal HIST id of the last explicitly labeled record (for GET-by-id tests). */
    private UUID namedRecordId;

    // ---- Symbolic name → UUID mapping (per scenario) ----

    private final Map<String, UUID> subjectUUIDs = new HashMap<>();
    private final Map<String, UUID> caregiverUUIDs = new HashMap<>();
    private final Map<String, UUID> factUUIDs = new HashMap<>();

    /**
     * Returns a deterministic UUID for a symbolic subject name (e.g. "pac-1").
     * The same name always produces the same UUID within a JVM run.
     */
    public UUID subjectUUID(String name) {
        return subjectUUIDs.computeIfAbsent(
                name, k -> UUID.nameUUIDFromBytes(("subject:" + k).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Returns a deterministic UUID for a symbolic caregiver name (e.g. "cg-1").
     */
    public UUID caregiverUUID(String name) {
        return caregiverUUIDs.computeIfAbsent(
                name,
                k -> UUID.nameUUIDFromBytes(("caregiver:" + k).getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Returns a deterministic UUID for a symbolic factId (e.g. "018f-0001").
     */
    public UUID factUUID(String name) {
        return factUUIDs.computeIfAbsent(
                name, k -> UUID.nameUUIDFromBytes(("factId:" + k).getBytes(StandardCharsets.UTF_8)));
    }

    // ---- Getters / setters ----

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCurrentToken() {
        return currentToken;
    }

    public void setCurrentToken(String currentToken) {
        this.currentToken = currentToken;
    }

    public ResponseEntity<String> getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(ResponseEntity<String> lastResponse) {
        this.lastResponse = lastResponse;
    }

    public Map<String, Object> getPendingFactRequest() {
        return pendingFactRequest;
    }

    public void setPendingFactRequest(Map<String, Object> pendingFactRequest) {
        this.pendingFactRequest = pendingFactRequest;
    }

    public UUID getFirstSubmittedFactId() {
        return firstSubmittedFactId;
    }

    public void setFirstSubmittedFactId(UUID firstSubmittedFactId) {
        this.firstSubmittedFactId = firstSubmittedFactId;
    }

    public UUID getFirstRecordId() {
        return firstRecordId;
    }

    public void setFirstRecordId(UUID firstRecordId) {
        this.firstRecordId = firstRecordId;
    }

    public List<ResponseEntity<String>> getConcurrentResponses() {
        return concurrentResponses;
    }

    public List<String> getRecordHashesBeforeVerify() {
        return recordHashesBeforeVerify;
    }

    public String getLastIngestedRecordHash() {
        return lastIngestedRecordHash;
    }

    public void setLastIngestedRecordHash(String lastIngestedRecordHash) {
        this.lastIngestedRecordHash = lastIngestedRecordHash;
    }

    public UUID getNamedRecordId() {
        return namedRecordId;
    }

    public void setNamedRecordId(UUID namedRecordId) {
        this.namedRecordId = namedRecordId;
    }
}
