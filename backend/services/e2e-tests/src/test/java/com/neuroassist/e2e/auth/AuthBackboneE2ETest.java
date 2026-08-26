package com.neuroassist.e2e.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.e2e.support.AuthClient;
import com.neuroassist.e2e.support.DevFixtures;
import com.neuroassist.e2e.support.E2EConfig;
import com.neuroassist.e2e.support.E2ETestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * AUTH — emissão do token no acs-service e sua aceitação pelos 7 resource servers.
 *
 * <p>O cenário que mais paga por si é o parametrizado: um único token tem de abrir uma porta em
 * cada serviço. Um serviço que configura o conversor de authorities lendo só {@code scope} (o
 * default do Spring) devolve 403 aqui e em nenhum gate de módulo — porque no gate de módulo o token
 * é forjado pelo próprio serviço.
 */
class AuthBackboneE2ETest extends E2ETestBase {

    @Test
    @DisplayName("AUTH-01 — login de dev devolve access + refresh e o tenant do principal")
    void loginDevolveParDeTokens() {
        AuthClient.Tokens tokens = AuthClient.login(E2EConfig.LOGIN, E2EConfig.SENHA);

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();
        assertThat(tokens.tokenType()).isEqualTo("Bearer");
        assertThat(tokens.expiresIn()).isPositive();
        assertThat(tokens.tenant()).isEqualTo(E2EConfig.TENANT);
        assertThat(tokens.papel()).isNotBlank();
    }

    @Test
    @DisplayName("AUTH-02 — o JWT carrega tenant_id e authorities, e NAO os aliases de E2E-021")
    void jwtCarregaApenasOClaimCanonico() {
        String payload = AuthClient.decodePayload(accessToken);

        assertThat(payload).contains("\"tenant_id\"");
        // As authorities finas vivem em `authorities`, nao em `scope` — e e por isso que todo
        // resource server precisa configurar o conversor para ler a uniao dos dois.
        assertThat(payload).contains("\"authorities\"");
        // E2E-021 removeu os aliases. Se voltarem, algum servico vai passar a aceitar o alias
        // errado em silencio, e esta assercao e o unico lugar que percebe.
        assertThat(payload).doesNotContain("\"empresaId\"");
        assertThat(payload).doesNotContain("\"tenant\":");
    }

    @Test
    @DisplayName("AUTH-03 — refresh token troca por um novo access token")
    void refreshEmiteNovoAccessToken() {
        AuthClient.attemptRefresh(refreshToken)
                .then()
                .statusCode(200)
                .body("accessToken", org.hamcrest.Matchers.not(org.hamcrest.Matchers.emptyString()))
                .body("tenant", org.hamcrest.Matchers.equalTo(E2EConfig.TENANT));
    }

    @Test
    @DisplayName("AUTH-04 — senha errada nao emite token (401)")
    void credencialInvalidaNaoEmiteToken() {
        AuthClient.attemptLogin(E2EConfig.LOGIN, "senha-obviamente-errada")
                .then()
                .statusCode(401)
                // O codigo de erro e o contrato estavel; o `title` deste ProblemDetail hoje diz
                // "Internal Server Error" (defeito reportado em E2E-013) e por isso nao e
                // assertado.
                .body("error.code", org.hamcrest.Matchers.equalTo("acs.auth.invalid-credentials"));
    }

    @Test
    @DisplayName("AUTH-05 — refresh token invalido e recusado")
    void refreshInvalidoERecusado() {
        int status = AuthClient.attemptRefresh("nao.e.um.token").getStatusCode();
        assertThat(status).isBetween(400, 499);
    }

    @Test
    @DisplayName("AUTH-06 — JWKS publico permite a qualquer resource server validar a assinatura")
    void jwksEstaPublicado() {
        io.restassured.RestAssured.given()
                .baseUri(E2EConfig.ACS)
                .get("/oauth2/jwks")
                .then()
                .statusCode(200)
                .body("keys.size()", org.hamcrest.Matchers.greaterThan(0));
    }

    /**
     * AUTH-07..13 — um token, sete serviços. Cada linha nomeia um endpoint de leitura real do
     * serviço, com o path copiado do {@code @RequestMapping} do controller correspondente.
     */
    @ParameterizedTest(name = "AUTH-07..13 — {0} aceita o token do ACS em {1}")
    @CsvSource({
        "acs-service,  /api/v1/acs/directory/tenants/TENANT",
        "pac-service,  /api/pac/v1/pacientes",
        "gest-service, /gest-service/detection-profile/devices/DEVICE",
        "edge-service, /api/v1/edge/devices",
        "hist-service, /api/v1/hist/fact-types",
        "evnt-service, /api/v1/events/active",
        "dash-service, /panel/summary"
    })
    void todoServicoAceitaOMesmoToken(String service, String path) {
        authed(baseUriOf(service)).get(resolve(path)).then().statusCode(200);
    }

    @ParameterizedTest(name = "AUTH-14..20 — {0} recusa chamada anonima em {1}")
    @CsvSource({
        "acs-service,  /api/v1/acs/directory/tenants/TENANT",
        "pac-service,  /api/pac/v1/pacientes",
        "gest-service, /gest-service/detection-profile/devices/DEVICE",
        "edge-service, /api/v1/edge/devices",
        "hist-service, /api/v1/hist/fact-types",
        "evnt-service, /api/v1/events/active",
        "dash-service, /panel/summary"
    })
    void nenhumServicoAtendeAnonimo(String service, String path) {
        anonymous(baseUriOf(service)).get(resolve(path)).then().statusCode(401);
    }

    private static String resolve(String path) {
        return path.replace("TENANT", E2EConfig.TENANT).replace("DEVICE", DevFixtures.DEVICE);
    }

    static String baseUriOf(String service) {
        return switch (service) {
            case "acs-service" -> E2EConfig.ACS;
            case "pac-service" -> E2EConfig.PAC;
            case "gest-service" -> E2EConfig.GEST;
            case "edge-service" -> E2EConfig.EDGE;
            case "hist-service" -> E2EConfig.HIST;
            case "evnt-service" -> E2EConfig.EVNT;
            case "dash-service" -> E2EConfig.DASH;
            default -> throw new IllegalArgumentException("Servico desconhecido: " + service);
        };
    }
}
