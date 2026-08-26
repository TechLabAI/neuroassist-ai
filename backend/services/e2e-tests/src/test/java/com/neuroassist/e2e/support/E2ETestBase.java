package com.neuroassist.e2e.support;

import static io.restassured.RestAssured.given;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;

/**
 * Base de toda classe da suíte.
 *
 * <p>A tag {@code e2e} é o que mantém estes testes fora do {@code build} padrão — ver o comentário
 * em {@code build.gradle.kts}. Herdar desta classe é, portanto, suficiente para o teste ficar
 * corretamente segregado; nenhuma classe da suíte deve declarar a tag por conta própria.
 *
 * <p>O login é feito uma vez por JVM ({@link #accessToken}) porque o token de dev dura 15 minutos e
 * refazer o handshake a cada teste só adicionaria ruído — a validação do fluxo de emissão é
 * responsabilidade explícita de {@code AuthBackboneE2ETest}, não um efeito colateral de setup.
 */
@Tag("e2e")
public abstract class E2ETestBase {

    protected static String accessToken;
    protected static String refreshToken;

    @BeforeAll
    static void bootstrap() {
        StackPreflight.ensureStackIsUp();
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        if (accessToken == null) {
            AuthClient.Tokens tokens = AuthClient.login(E2EConfig.LOGIN, E2EConfig.SENHA);
            accessToken = tokens.accessToken();
            refreshToken = tokens.refreshToken();
        }
    }

    /** Request autenticada e com o tenant canônico — o caso feliz de qualquer chamada. */
    protected static RequestSpecification authed(String baseUri) {
        return authedWithTenant(baseUri, E2EConfig.TENANT);
    }

    /** Variante que permite divergir o header do claim — usada só pelos cenários negativos. */
    protected static RequestSpecification authedWithTenant(String baseUri, String tenant) {
        RequestSpecification spec =
                given().spec(
                                new RequestSpecBuilder()
                                        .setBaseUri(baseUri)
                                        .setContentType(ContentType.JSON)
                                        .build())
                        .header("Authorization", "Bearer " + accessToken);
        if (tenant != null) {
            spec = spec.header(E2EConfig.TENANT_HEADER, tenant);
        }
        // Sem filtro de log permanente: os filtros do RestAssured escrevem em System.out, o que a
        // convenção do projeto proíbe. O dump só sai quando uma asserção falha, via
        // enableLoggingOfRequestAndResponseIfValidationFails() — que é quando ele é útil.
        return spec;
    }

    /** Sem Authorization — para provar que o resource server rejeita anônimos. */
    protected static RequestSpecification anonymous(String baseUri) {
        return given().baseUri(baseUri)
                .contentType(ContentType.JSON)
                .header(E2EConfig.TENANT_HEADER, E2EConfig.TENANT);
    }
}
