package com.neuroassist.edge.bdd;

import org.springframework.http.ResponseEntity;

/**
 * Per-scenario shared state for BDD step definitions. Registered as a {@code @Bean} with scope
 * {@code cucumber-glue} in {@link CucumberSpringContext.TestConfig} so that a fresh instance is
 * created for each Cucumber scenario and destroyed at the end. Step definition classes inject this
 * bean to share state (HTTP response, JWT tokens, captured ETag) without coupling to each other
 * directly.
 *
 * <p>Not thread-safe — Cucumber scenarios are single-threaded by default.
 */
public class TestContext {

    /** Last HTTP response stored by a When step for use in subsequent Then/And steps. */
    private ResponseEntity<String> lastResponse;

    /** ETag captured from a liveness response for use in If-None-Match conditional GET steps. */
    private String capturedETag;

    /** Device JWT (scope=edge.device, sub=deviceId) for heartbeat requests. */
    private String deviceToken;

    /** Operator JWT (scope=edge.read edge.manage) for DASH liveness poll and manage requests. */
    private String operatorToken;

    /** Service JWT (scope=edge.internal) for GEST-to-EDGE internal requests. */
    private String serviceToken;

    public ResponseEntity<String> getLastResponse() {
        return lastResponse;
    }

    public void setLastResponse(ResponseEntity<String> lastResponse) {
        this.lastResponse = lastResponse;
    }

    public String getCapturedETag() {
        return capturedETag;
    }

    public void setCapturedETag(String capturedETag) {
        this.capturedETag = capturedETag;
    }

    public String getDeviceToken() {
        return deviceToken;
    }

    public void setDeviceToken(String deviceToken) {
        this.deviceToken = deviceToken;
    }

    public String getOperatorToken() {
        return operatorToken;
    }

    public void setOperatorToken(String operatorToken) {
        this.operatorToken = operatorToken;
    }

    public String getServiceToken() {
        return serviceToken;
    }

    public void setServiceToken(String serviceToken) {
        this.serviceToken = serviceToken;
    }
}
