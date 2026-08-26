package com.neuroassist.gest.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Aplica as migrations <b>reais</b> ao container de teste, antes de o Spring subir (E2E-022).
 *
 * <p><b>O problema que isto resolve.</b> As suítes rodavam com {@code ddl-auto=create-drop}: o
 * Hibernate desenhava o schema nos testes enquanto o Flyway desenha o schema em produção. Qualquer
 * divergência entre uma migration e um mapeamento JPA ficava invisível até o runtime.
 *
 * <p>O projeto já pagou duas vezes:
 *
 * <ul>
 *   <li><b>C-12</b> — {@code hist_outbox.status} era ENUM nativo no Flyway e {@code String} na
 *       entity. O Postgres não faz coerção implícita de varchar para ENUM em INSERT, então
 *       <i>toda</i> gravação no outbox falhava em produção. Nos testes a coluna nascia varchar e o
 *       ENUM nunca entrava em cena.
 *   <li><b>E2E-017</b> — o evnt-service quase mapeou a {@code hist_outbox} do pac-service, porque
 *       os dois serviços compartilham o schema do tenant e o nome estava tomado. O IT rodava contra
 *       PostgreSQL real e não viu: quem desenhava o schema era o Hibernate, a partir da entity, e a
 *       tabela do PAC não existia lá.
 * </ul>
 *
 * <p><b>Por que ler os arquivos do disco e não o classpath.</b> As migrations vivem noutro módulo
 * Gradle ({@code neuroassist-ai-migrations}). Depender dele traria a aplicação inteira para o
 * classpath de teste; apontar para o diretório mantém uma única fonte — os testes leem exatamente
 * os arquivos que o deploy aplica, e não uma cópia que pode envelhecer.
 *
 * <p><b>Global antes de empresa</b>, como no deploy: o template de empresa pode referenciar o
 * registro global, e o inverso não.
 */
final class SchemaMigrations {

    private static final String GLOBAL_SCHEMA = "neuroassist_global";

    /**
     * A role que {@code V0006__hist_patient_encryption_key.sql} referencia em {@code GRANT}. Em
     * produção é {@code neuroassist}; aqui é o usuário do container, senão a migration falha com
     * {@code role "neuroassist" does not exist} e o teste estaria medindo a ausência de uma role.
     */
    private static final String HIST_ROLE_PLACEHOLDER = "hist_service_role";

    private SchemaMigrations() {}

    /**
     * @param tenantSchemas todos os schemas de tenant que os testes usam; cada um recebe o template
     *     de empresa completo. GEST usa dois: {@code t_emp_test} (tenant primário) e {@code
     *     t_emp_other} (tenant secundário para testes de isolamento cross-tenant, INT-GEST-005).
     */
    static void applyTo(PostgreSQLContainer<?> postgres, List<String> tenantSchemas) {
        migrate(postgres, GLOBAL_SCHEMA, location("global"), postgres.getUsername());
        for (String schema : tenantSchemas) {
            migrate(postgres, schema, location("empresa"), postgres.getUsername());
        }
    }

    private static void migrate(
            PostgreSQLContainer<?> postgres, String schema, String location, String histRole) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .schemas(schema)
                .defaultSchema(schema)
                .locations(location)
                .placeholders(Map.of("schema", schema, HIST_ROLE_PLACEHOLDER, histRole))
                // Mesma razão do deploy: as faixas de V por módulo (GEST V0400-V0499, …) fazem
                // toda migration nova de faixa baixa ser, por construção, out of order em relação
                // às faixas mais altas já aplicadas.
                .outOfOrder(true)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .load()
                .migrate();
    }

    /**
     * Resolve {@code db/migration/<half>} a partir do diretório do projeto. O working directory do
     * teste é o diretório do serviço, então o caminho sobe até {@code services/}.
     */
    private static String location(String half) {
        Path path =
                Paths.get(
                                "..",
                                "neuroassist-ai-migrations",
                                "src",
                                "main",
                                "resources",
                                "db",
                                "migration",
                                half)
                        .toAbsolutePath()
                        .normalize();
        if (!Files.isDirectory(path)) {
            // Falha alta e explícita: um caminho errado faria o Flyway aplicar ZERO migrations e
            // reportar sucesso, e o teste voltaria a validar um schema vazio sem dizer nada.
            throw new IllegalStateException(
                    "Migrations não encontradas em "
                            + path
                            + " — o teste validaria um schema vazio");
        }
        return "filesystem:" + path;
    }
}
