package com.neuroassist.pac.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base class for PAC integration tests.
 *
 * <p>Provides:
 *
 * <ul>
 *   <li>Singleton PostgreSQL Testcontainer (init-schema.sql creates the test schemas).
 *   <li>Singleton WireMock servers for GEST/ACS/EDGE/HIST on <b>dynamic ports</b> (PASSO-0 fix —
 *       avoids FatalStartupException on port conflicts). Ports are injected via {@link
 *       DynamicPropertySource}.
 *   <li>JWK WireMock server for OAuth2 JWT validation — Spring Security fetches the RSA public key
 *       from this server at startup. URL injected via {@link DynamicPropertySource}.
 * </ul>
 *
 * <p>All WireMock servers use dynamic port 0. Their resolved base URLs are registered as Spring
 * properties before the application context is created, ensuring Feign clients and the JWT resource
 * server point to the correct WireMock instances.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
        // PostgreSQL Testcontainer (singleton pattern — started once per JVM).
        POSTGRES =
                new PostgreSQLContainer<>("postgres:16-alpine")
                        .withDatabaseName("neuroassist-ai_test")
                        .withUsername("test")
                        .withPassword("test")
                        .withInitScript("init-schema.sql");
        POSTGRES.start();

        // E2E-022 — as migrations REAIS, aplicadas antes de o Spring subir, exatamente como no
        // deploy. Com `ddl-auto=validate` no application-test.yml, uma divergência entre migration
        // e mapeamento JPA passa a derrubar o contexto em vez de aparecer só em produção.
        SchemaMigrations.applyTo(
                POSTGRES, java.util.List.of("empresa-abc", "empresa-xyz", "t_emp_test"));

        // INT-PAC-006 (P8) — WireMock servers (singleton, dynamic ports, PASSO-0 fix).
        // startAll() is a no-op — servers are initialized in ExternalServicesWireMock's static
        // block which runs when this class is first loaded (before DynamicPropertySource).
        // D-534 resolved: org.wiremock:wiremock-standalone:3.9.1 (Jetty 12 + Jakarta embedded).
        ExternalServicesWireMock.startAll();
    }

    /**
     * Injects dynamic WireMock URLs and PostgreSQL DataSource properties into the Spring
     * application context before it is created.
     *
     * <p>DynamicPropertySource takes precedence over {@code application-test.yml}, so all fixed
     * URLs in the YML are overridden by the actual dynamic ports.
     */
    /**
     * A URL do Testcontainers já vem com query string ({@code ?loggerLevel=OFF}), então o separador
     * precisa ser {@code &}. Concatenar um segundo {@code ?} produz uma URL que o driver aceita e
     * ignora — o parâmetro some em silêncio e a validação falha por um motivo que não é o real.
     */
    private static String jdbcUrlWithSearchPath() {
        String url = POSTGRES.getJdbcUrl();
        return url
                + (url.contains("?") ? "&" : "?")
                + "currentSchema=t_emp_test,neuroassist_global";
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // DataSource (Testcontainers PostgreSQL)
        // E2E-022 — `currentSchema` com um schema de tenant à frente do global.
        //
        // Com `ddl-auto=validate`, o Hibernate valida as entities contra a conexão de bootstrap,
        // que não tem tenant vinculado. Sem o schema de tenant no search_path ele olharia só o
        // global, não acharia `paciente` e derrubaria o contexto — reprovando por ausência de
        // search_path, não por divergência de mapeamento, que é o que se quer medir.
        registry.add("spring.datasource.url", BaseIntegrationTest::jdbcUrlWithSearchPath);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);

        // D-534 resolved: Feign client base URLs → WireMock dynamic ports (INT-PAC-006, PASSO-0
        // fix)
        registry.add("neuroassist.integration.gest.url", ExternalServicesWireMock::gestBaseUrl);
        registry.add("neuroassist.integration.acs.url", ExternalServicesWireMock::acsBaseUrl);
        registry.add("neuroassist.integration.edge.url", ExternalServicesWireMock::edgeBaseUrl);
        registry.add("neuroassist.integration.hist.url", ExternalServicesWireMock::histBaseUrl);

        // Service identity — the ACS WireMock also serves POST /oauth2/token/service, so the
        // hist-service Feign client can swap the caregiver token for pac-service's own token.
        registry.add(
                "neuroassist.service-identity.token-uri",
                () -> ExternalServicesWireMock.acsBaseUrl() + "/oauth2/token/service");

        // OAuth2 JWT validation — JWK WireMock serves the RSA public key (P9, PASSO-0 fix)
        registry.add(
                "spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                () -> ExternalServicesWireMock.jwkBaseUrl() + "/.well-known/jwks.json");
    }

    /**
     * Resets all WireMock stub registrations before each test.
     *
     * <p>This ensures stubs registered in one test do not bleed into another. Subclasses that
     * override {@code @BeforeEach} must call {@code super.resetWireMock()} or invoke {@link
     * ExternalServicesWireMock#resetAll()} themselves.
     */
    @BeforeEach
    void resetWireMock() {
        ExternalServicesWireMock.resetAll();
    }
}
