package com.neuroassist.mig.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuroassist.mig.application.port.in.ProvisionTenantSchemaUseCase;
import com.neuroassist.mig.application.service.UnknownTenantException;
import com.neuroassist.mig.domain.model.ProvisioningStatus;
import com.neuroassist.mig.domain.model.TenantSchemaProvisioning;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * E2E-016 — provisioning against a real PostgreSQL.
 *
 * <p>This is the test the previous implementation could not have had. The stub returned {@code
 * READY} from a literal, so there was nothing to point a database at; the only way to discover that
 * no schema existed was to look for it by hand, and every E2E session used the pre-seeded tenant
 * instead.
 *
 * <p>Singleton container in a static initialiser, not {@code @Testcontainers} + {@code @Container}
 * (CLAUDE.md).
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "api"})
@DisplayName("Provisionamento de tenant contra PostgreSQL real")
class TenantSchemaProvisioningIT {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("mig_test")
                    .withUsername("mig")
                    .withPassword("mig");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.flyway.enabled", () -> "false");
        // V0006 do template concede privilégios a esta role. Em produção ela é `neuroassist`;
        // aqui é o usuário do container. Sem isto a migration falha com
        // `role "neuroassist" does not exist` — e o teste estaria medindo a ausência de uma
        // role, não o provisionamento.
        registry.add("neuroassist.mig.hist-service-role", POSTGRES::getUsername);
    }

    @Autowired private ProvisionTenantSchemaUseCase useCase;
    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;

    @BeforeAll
    static void createGlobalRegistry() {
        JdbcTemplate bootstrap =
                new JdbcTemplate(
                        new org.springframework.jdbc.datasource.DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        bootstrap.execute("CREATE SCHEMA IF NOT EXISTS neuroassist_global");
        // Only the two columns MIG reads. A fuller copy of acs-service's table would drift from it
        // silently, and this test is not the place that owns its shape.
        bootstrap.execute(
                """
                CREATE TABLE IF NOT EXISTS neuroassist_global.tenant (
                    tenant_id   uuid PRIMARY KEY,
                    schema_nome varchar(80) NULL
                )
                """);
        bootstrap.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        bootstrap.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
    }

    private JdbcTemplate jdbc() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(dataSource);
        }
        return jdbc;
    }

    private UUID registerTenant() {
        UUID tenantId = UUID.randomUUID();
        jdbc().update("INSERT INTO neuroassist_global.tenant (tenant_id) VALUES (?)", tenantId);
        return tenantId;
    }

    private static String schemaFor(UUID tenantId) {
        return "t_" + tenantId.toString().replace("-", "");
    }

    @Test
    @DisplayName("cria o schema e aplica o template de empresa inteiro")
    void provisioningCreatesAndMigratesTheSchema() {
        UUID tenantId = registerTenant();
        String schema = schemaFor(tenantId);

        assertThat(schemaExists(schema)).as("pré-condição: schema não existe").isFalse();

        TenantSchemaProvisioning result = useCase.provision(tenantId, schema);

        assertThat(result.status()).isEqualTo(ProvisioningStatus.READY);
        assertThat(schemaExists(schema)).as("o schema tem de existir de verdade").isTrue();
        assertThat(result.appliedVersions()).isNotEmpty();
        assertThat(result.currentVersion()).isNotNull();

        // A prova de que o template foi aplicado, e não só o schema criado: as tabelas que os
        // serviços de negócio esperam encontrar estão lá.
        assertThat(tableExists(schema, "paciente")).isTrue();
        assertThat(tableExists(schema, "directory_outbox")).isTrue();
    }

    @Test
    @DisplayName("idempotente: provisionar duas vezes não aplica nada na segunda")
    void provisioningTwiceIsANoOpTheSecondTime() {
        UUID tenantId = registerTenant();
        String schema = schemaFor(tenantId);

        TenantSchemaProvisioning first = useCase.provision(tenantId, schema);
        TenantSchemaProvisioning second = useCase.provision(tenantId, schema);

        assertThat(first.alreadyCurrent()).isFalse();
        assertThat(second.alreadyCurrent()).as("re-provisionar já em head é no-op").isTrue();
        assertThat(second.status()).isEqualTo(ProvisioningStatus.READY);
        assertThat(second.currentVersion()).isEqualTo(first.currentVersion());
    }

    @Test
    @DisplayName("o lock é liberado — duas chamadas seguidas não colidem")
    void theAdvisoryLockIsReleasedBetweenCalls() {
        UUID tenantId = registerTenant();
        String schema = schemaFor(tenantId);

        useCase.provision(tenantId, schema);

        // Se o lock vazasse na conexão do pool, esta segunda chamada responderia
        // run-in-progress para sempre.
        assertThat(useCase.provision(tenantId, schema).status())
                .isEqualTo(ProvisioningStatus.READY);
    }

    @Test
    @DisplayName("tenant não registrado é recusado e nenhum schema é criado")
    void unknownTenantCreatesNothing() {
        UUID unregistered = UUID.randomUUID();
        String schema = schemaFor(unregistered);

        assertThatThrownBy(() -> useCase.provision(unregistered, schema))
                .isInstanceOf(UnknownTenantException.class);

        assertThat(schemaExists(schema)).isFalse();
    }

    @Test
    @DisplayName("status de tenant cujo schema não existe é FAILED, nunca READY")
    void statusOfAMissingSchemaIsFailed() {
        UUID tenantId = registerTenant();
        jdbc().update(
                        "UPDATE neuroassist_global.tenant SET schema_nome = ? WHERE tenant_id = ?",
                        "t_nao_existe",
                        tenantId);

        assertThat(useCase.statusOf(tenantId)).isEqualTo(ProvisioningStatus.FAILED);
    }

    private boolean schemaExists(String schema) {
        Integer n =
                jdbc().queryForObject(
                                "SELECT count(*) FROM information_schema.schemata WHERE"
                                        + " schema_name = ?",
                                Integer.class,
                                schema);
        return n != null && n > 0;
    }

    private boolean tableExists(String schema, String table) {
        Integer n =
                jdbc().queryForObject(
                                "SELECT count(*) FROM information_schema.tables WHERE"
                                        + " table_schema = ? AND table_name = ?",
                                Integer.class,
                                schema,
                                table);
        return n != null && n > 0;
    }
}
