package com.neuroassist.evnt.integration;

import java.util.List;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class BaseIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES;

    static {
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
        SchemaMigrations.applyTo(POSTGRES, List.of("t_emp_test"));
    }

    /**
     * A URL do Testcontainers já vem com query string ({@code ?loggerLevel=OFF}), então o separador
     * precisa ser {@code &}. Concatenar um segundo {@code ?} produz uma URL que o driver aceita e
     * ignora — o parâmetro some em silêncio e a validação falha por um motivo que não é o real.
     *
     * <p>{@code currentSchema} precisa ter um schema de tenant à frente do global: com {@code
     * ddl-auto=validate}, o Hibernate valida as entities contra a conexão de bootstrap, que não tem
     * tenant vinculado. Sem o schema de tenant no search_path ele olharia só o global, não acharia
     * as tabelas do EVNT e derrubaria o contexto — reprovando por ausência de search_path, não por
     * divergência de mapeamento.
     */
    private static String jdbcUrlWithSearchPath() {
        String url = POSTGRES.getJdbcUrl();
        return url
                + (url.contains("?") ? "&" : "?")
                + "currentSchema=t_emp_test,neuroassist_global";
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // E2E-022 — `currentSchema` com um schema de tenant à frente do global.
        registry.add("spring.datasource.url", BaseIntegrationTest::jdbcUrlWithSearchPath);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
