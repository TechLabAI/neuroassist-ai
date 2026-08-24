package com.neuroassist.gest.bdd;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Scope;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

/**
 * Cliente HTTP dos cenários BDD — uma única porta de entrada para o serviço sob teste.
 *
 * <p>Existe para que todo step atravesse o caminho real: filtro de segurança, {@code
 * TenantContextFilter}, {@code @PreAuthorize}, controller, serviço, banco. Um step que consultasse
 * o repositório direto passaria mesmo com a autorização quebrada.
 *
 * <p>O token vem do {@link ScenarioState}, e não é opcional por acidente: {@code currentToken} nulo
 * produz uma requisição anônima de propósito, que é como os cenários de 401 são escritos.
 */
@Slf4j
@Component
@Scope("cucumber-glue")
@RequiredArgsConstructor
public class GestHttp {

    private final TestRestTemplate restTemplate;
    private final ScenarioState state;
    private final ObjectMapper objectMapper;

    // ── Verbos ───────────────────────────────────────────────────────────────

    public ResponseEntity<String> get(String path) {
        return exchange(HttpMethod.GET, path, null, headers());
    }

    /** GET condicional — {@code If-None-Match} é o que faz o 304 do pull da borda existir. */
    public ResponseEntity<String> getIfNoneMatch(String path, String etag) {
        HttpHeaders h = headers();
        h.setIfNoneMatch(etag);
        return exchange(HttpMethod.GET, path, null, h);
    }

    public ResponseEntity<String> post(String path, String jsonBody) {
        HttpHeaders h = headers();
        h.setContentType(MediaType.APPLICATION_JSON);
        return exchange(HttpMethod.POST, path, jsonBody, h);
    }

    private ResponseEntity<String> exchange(
            HttpMethod method, String path, String body, HttpHeaders h) {
        ResponseEntity<String> response =
                restTemplate.exchange(path, method, new HttpEntity<>(body, h), String.class);
        state.lastResponse = response;
        log.debug("{} {} -> {}", method, path, response.getStatusCode().value());
        return response;
    }

    private HttpHeaders headers() {
        HttpHeaders h = new HttpHeaders();
        if (state.currentToken != null) {
            h.setBearerAuth(state.currentToken);
        }
        return h;
    }

    // ── Leitura do corpo ─────────────────────────────────────────────────────

    /** Corpo da última resposta como árvore JSON. Falha alto se não houver corpo legível. */
    public JsonNode lastJson() {
        ResponseEntity<String> response = state.lastResponse;
        if (response == null || response.getBody() == null || response.getBody().isBlank()) {
            throw new AssertionError(
                    "Nenhum corpo de resposta para ler — o step anterior não fez a requisição, ou"
                            + " ela voltou vazia (status="
                            + (response == null ? "nenhuma" : response.getStatusCode())
                            + ")");
        }
        return parse(response.getBody());
    }

    public JsonNode parse(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new AssertionError("Corpo de resposta não é JSON válido: " + json, e);
        }
    }

    public String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao serializar corpo de requisição", e);
        }
    }
}
