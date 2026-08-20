package com.neuroassist.pac.bdd;

import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * HTTP request helper for BDD step definitions (PAC-025, P9).
 *
 * <p>Centralises JWT + header injection so individual step classes don't duplicate request
 * construction. Uses {@link TestRestTemplate} which does NOT throw on 4xx/5xx — the raw {@link
 * ResponseEntity} is always returned for assertion.
 *
 * <p>Not annotated with @Component — registered exclusively via {@link BddTestConfiguration} to
 * prevent contamination of non-BDD Spring test contexts (e.g. SecurityConfigTest with
 * webEnvironment=MOCK which has no TestRestTemplate).
 */
public class HttpTestHelper {

    private final TestRestTemplate restTemplate;
    private final ScenarioContext ctx;

    public HttpTestHelper(TestRestTemplate restTemplate, ScenarioContext ctx) {
        this.restTemplate = restTemplate;
        this.ctx = ctx;
    }

    // -------------------------------------------------------------------------
    // POST
    // -------------------------------------------------------------------------

    public ResponseEntity<String> post(String path, String body) {
        HttpHeaders headers = buildHeaders(null);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(path, HttpMethod.POST, entity, String.class);
    }

    public ResponseEntity<String> postWithEtag(String path, String ifMatch) {
        HttpHeaders headers = buildHeaders(ifMatch);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(null, headers);
        return restTemplate.exchange(path, HttpMethod.POST, entity, String.class);
    }

    public ResponseEntity<String> postWithEtagAndBody(String path, String ifMatch, String body) {
        HttpHeaders headers = buildHeaders(ifMatch);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(path, HttpMethod.POST, entity, String.class);
    }

    // -------------------------------------------------------------------------
    // GET
    // -------------------------------------------------------------------------

    public ResponseEntity<String> get(String path) {
        HttpEntity<Void> entity = new HttpEntity<>(buildHeaders(null));
        return restTemplate.exchange(path, HttpMethod.GET, entity, String.class);
    }

    public ResponseEntity<String> getWithHeader(
            String path, String extraHeaderName, String extraHeaderValue) {
        HttpHeaders headers = buildHeaders(null);
        headers.add(extraHeaderName, extraHeaderValue);
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        return restTemplate.exchange(path, HttpMethod.GET, entity, String.class);
    }

    // -------------------------------------------------------------------------
    // PUT
    // -------------------------------------------------------------------------

    public ResponseEntity<String> put(String path, String body, String ifMatch) {
        HttpHeaders headers = buildHeaders(ifMatch);
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> entity = new HttpEntity<>(body, headers);
        return restTemplate.exchange(path, HttpMethod.PUT, entity, String.class);
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private HttpHeaders buildHeaders(String ifMatch) {
        HttpHeaders headers = new HttpHeaders();
        if (ctx.getCurrentJwt() != null) {
            headers.setBearerAuth(ctx.getCurrentJwt());
        }
        if (ctx.isSendEmpresaId() && ctx.getCurrentEmpresaId() != null) {
            headers.add("X-Empresa-Id", ctx.getCurrentEmpresaId());
        }
        if (ifMatch != null && !ifMatch.isBlank()) {
            headers.add("If-Match", ifMatch);
        }
        return headers;
    }
}
