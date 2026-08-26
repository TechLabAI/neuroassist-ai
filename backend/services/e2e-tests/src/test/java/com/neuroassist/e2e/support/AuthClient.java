package com.neuroassist.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.Base64;

/**
 * Cliente do backbone de autenticação do acs-service.
 *
 * <p>Os nomes de campo abaixo espelham {@code com.neuroassist.acs.api.dto.AutenticacaoRequest} e
 * {@code AutenticacaoResponse} verbatim. Isso não é preciosismo: o catálogo de defeitos em
 * plan-e2e-integration.md §11 mostra que um teste escrito a partir da suposição do consumidor passa
 * exatamente quando a suposição está errada. Ao mexer aqui, abra os dois records antes.
 *
 * <p>Nota sobre {@code AutenticacaoResponse.tenant}: o corpo HTTP chama o campo de {@code tenant},
 * mas o <b>claim</b> dentro do JWT chama-se {@code tenant_id} — e desde E2E-021 é o único, sem os
 * aliases {@code tenant}/{@code empresaId}. Não confunda os dois nomes.
 */
public final class AuthClient {

    /** Projeção dos campos de {@code AutenticacaoResponse} que a suíte consome. */
    public record Tokens(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            String tenant,
            String sub,
            String papel) {}

    private AuthClient() {}

    public static Tokens login(String login, String senha) {
        Response response =
                given().baseUri(E2EConfig.ACS)
                        .contentType(ContentType.JSON)
                        .body(
                                """
                                {"grantType":"password","login":"%s","senha":"%s"}"""
                                        .formatted(login, senha))
                        .post("/oauth2/token")
                        .then()
                        .statusCode(200)
                        .extract()
                        .response();
        return toTokens(response);
    }

    /** POST /oauth2/token — sem assertar status, para os cenários de credencial inválida. */
    public static Response attemptLogin(String login, String senha) {
        return given().baseUri(E2EConfig.ACS)
                .contentType(ContentType.JSON)
                .body(
                        """
                        {"grantType":"password","login":"%s","senha":"%s"}"""
                                .formatted(login, senha))
                .post("/oauth2/token");
    }

    /** POST /oauth2/token/refresh — body é {@code RefreshTokenRequest{refreshToken}}. */
    public static Response attemptRefresh(String token) {
        return given().baseUri(E2EConfig.ACS)
                .contentType(ContentType.JSON)
                .body(
                        """
                      {"refreshToken":"%s"}"""
                                .formatted(token))
                .post("/oauth2/token/refresh");
    }

    private static Tokens toTokens(Response response) {
        return new Tokens(
                response.path("accessToken"),
                response.path("refreshToken"),
                response.path("tokenType"),
                ((Number) response.path("expiresIn")).longValue(),
                response.path("tenant"),
                response.path("sub"),
                response.path("papel"));
    }

    /**
     * Decodifica o payload do JWT sem validar assinatura.
     *
     * <p>Validar a assinatura aqui seria testar a biblioteca, não o sistema: quem prova que o token
     * é válido são os 7 resource servers, aceitando-o. O que a suíte precisa da decodificação é
     * apenas inspecionar quais claims o ACS emitiu — em particular a ausência dos aliases removidos
     * por E2E-021.
     */
    public static String decodePayload(String jwt) {
        String[] parts = jwt.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException(
                    "Nao e um JWT compacto: " + parts.length + " partes");
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]));
    }

    /**
     * Extrai um claim de topo do payload do JWT, como string.
     *
     * <p>Só resolve claims escalares — é o que a suíte precisa ({@code sub}, {@code tenant_id}). Um
     * claim ausente devolve {@code null} em vez de exceção, para que o chamador decida se aquilo é
     * um defeito (E2E-021: {@code tenant_id} ausente é) ou apenas informação que não veio.
     */
    public static String claim(String jwt, String nome) {
        String payload = decodePayload(jwt);
        java.util.regex.Matcher m =
                java.util.regex.Pattern.compile(
                                "\""
                                        + java.util.regex.Pattern.quote(nome)
                                        + "\"\\s*:\\s*\"([^\"]*)\"")
                        .matcher(payload);
        return m.find() ? m.group(1) : null;
    }
}
