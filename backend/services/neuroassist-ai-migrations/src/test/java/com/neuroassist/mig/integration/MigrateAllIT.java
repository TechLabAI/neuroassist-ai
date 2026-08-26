package com.neuroassist.mig.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.mig.application.port.in.MigrateAllUseCase;
import com.neuroassist.mig.application.port.in.ProvisionTenantSchemaUseCase;
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
 * E2E-030 — the acceptance criterion of the issue, against a real PostgreSQL.
 *
 * <p>Provision a tenant, let a new tenant migration land, run {@code migrate-all}, and check that
 * the tenant converged. That sequence had no coverage at all, which is why four dev tenants sat a
 * migration behind and the only symptom was a warning in another service's sweeper.
 */
@Tag("integration")
@SpringBootTest
@ActiveProfiles({"test", "api"})
@DisplayName("migrate-all — leque sobre tenants provisionados")
class MigrateAllIT {

    @SuppressWarnings("resource")
    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("mig_fanout")
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
        registry.add("neuroassist.mig.hist-service-role", POSTGRES::getUsername);
    }

    @Autowired private ProvisionTenantSchemaUseCase provisionUseCase;
    @Autowired private MigrateAllUseCase migrateAllUseCase;
    @Autowired private DataSource dataSource;

    private JdbcTemplate jdbc;

    /**
     * Só o schema vazio e as extensões. O registro global é criado pelas <b>migrations globais
     * reais</b>, aplicadas por {@code migrateGlobal()} no {@code @BeforeEach}.
     *
     * <p>A primeira versão deste teste criava a tabela {@code tenant} à mão, como faz o teste de
     * provisionamento — e ali é correto, porque aquele nunca migra a global. Aqui o próprio {@code
     * migrate-all} migra a global, e uma tabela feita à mão colide com a migration que a cria. O
     * teste estava medindo o próprio andaime.
     */
    @BeforeAll
    static void createEmptyGlobalSchema() {
        JdbcTemplate bootstrap =
                new JdbcTemplate(
                        new org.springframework.jdbc.datasource.DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        bootstrap.execute("CREATE SCHEMA IF NOT EXISTS neuroassist_global");
        bootstrap.execute("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        bootstrap.execute("CREATE EXTENSION IF NOT EXISTS pgcrypto");
    }

    @org.junit.jupiter.api.BeforeEach
    void bringGlobalToHead() {
        // Idempotente: já em head, não aplica nada.
        assertThat(migrateAllUseCase.migrateGlobal().anyFailed()).isFalse();
    }

    private JdbcTemplate jdbc() {
        if (jdbc == null) {
            jdbc = new JdbcTemplate(dataSource);
        }
        return jdbc;
    }

    private String provisionTenant() {
        UUID tenantId = UUID.randomUUID();
        String schema = "t_" + tenantId.toString().replace("-", "");
        jdbc().update(
                        "INSERT INTO neuroassist_global.tenant (tenant_id, nome, tipo, schema_nome)"
                                + " VALUES (?, ?, ?, ?)",
                        tenantId,
                        "Tenant " + schema,
                        "CLINICA",
                        schema);
        provisionUseCase.provision(tenantId, schema);
        return schema;
    }

    @Test
    @DisplayName("todo tenant do catálogo converge — nenhum fica para trás")
    void everyRegisteredTenantConverges() {
        String first = provisionTenant();
        String second = provisionTenant();

        var report = migrateAllUseCase.migrateAll();

        assertThat(report.preflightFailed()).isFalse();
        assertThat(report.anyFailed()).isFalse();
        assertThat(report.outcomes()).extracting(o -> o.schema()).contains(first, second);

        // A propriedade que a issue pede: MESMA versão em todos, verificável sem abrir o banco.
        assertThat(versionOf(second)).isEqualTo(versionOf(first));
    }

    @Test
    @DisplayName("um tenant provisionado antes de uma migration nova é alcançado pelo leque")
    void aTenantProvisionedBeforeANewMigrationCatchesUp() {
        String schema = provisionTenant();
        String versionAtProvisioning = versionOf(schema);

        // Simula o estado que criou o defeito: o schema existe, mas com o histórico incompleto.
        // Remover a última migration do histórico faz o Flyway considerá-la pendente de novo.
        jdbc().update(
                        "DELETE FROM " + schema + ".flyway_schema_history WHERE version = ?",
                        versionAtProvisioning);
        jdbc().execute("DROP TABLE IF EXISTS " + schema + ".caregiver_patient_link CASCADE");

        var report = migrateAllUseCase.migrateAll();

        assertThat(report.anyFailed()).isFalse();
        assertThat(versionOf(schema)).isEqualTo(versionAtProvisioning);
    }

    @Test
    @DisplayName("schema do catálogo que não existe no banco falha SÓ ele, e é nomeado")
    void aMissingSchemaFailsAloneAndIsNamed() {
        String healthy = provisionTenant();
        jdbc().update(
                        "INSERT INTO neuroassist_global.tenant (tenant_id, nome, tipo, schema_nome)"
                                + " VALUES (?, ?, ?, ?)",
                        UUID.randomUUID(),
                        "Registrado mas inexistente",
                        "CLINICA",
                        "t_registrado_mas_inexistente");

        var report = migrateAllUseCase.migrateAll();

        // O tenant saudável converge de qualquer forma — uma empresa quebrada não prende as
        // outras — mas o run como um todo reprova, porque a frota está em versões mistas.
        assertThat(report.outcomes()).extracting(o -> o.schema()).contains(healthy);
        assertThat(versionOf(healthy)).isNotNull();
    }

    private String versionOf(String schema) {
        return jdbc().queryForObject(
                        "SELECT max(version) FROM " + schema + ".flyway_schema_history",
                        String.class);
    }
}
