package com.neuroassist.mig.infrastructure.persistence;

import com.neuroassist.mig.application.port.out.TenantCatalogPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the global tenant registry with plain JDBC and a fully qualified table name (E2E-016).
 *
 * <p>Qualified on purpose: this service talks to many schemas in one process, and a query that
 * relies on {@code search_path} would read whichever schema happened to be current. The registry
 * lives in exactly one place.
 *
 * <p>Read-only. acs-service owns {@code neuroassist_global.tenant}; MIG asks it two questions and
 * writes nothing — a second writer to a registry is how two sources of truth start.
 */
@Slf4j
@Component
public class JdbcTenantCatalogAdapter implements TenantCatalogPort {

    private final JdbcTemplate jdbcTemplate;
    private final String globalSchema;

    public JdbcTenantCatalogAdapter(
            DataSource dataSource,
            @Value("${neuroassist.mig.global-schema:neuroassist_global}") String globalSchema) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.globalSchema = globalSchema;
    }

    @Override
    public Optional<String> schemaOf(UUID tenantId) {
        List<String> rows =
                jdbcTemplate.queryForList(
                        "SELECT schema_nome FROM " + globalSchema + ".tenant WHERE tenant_id = ?",
                        String.class,
                        tenantId);
        return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(rows.get(0));
    }

    @Override
    public List<String> allTenantSchemas() {
        return jdbcTemplate.queryForList(
                "SELECT schema_nome FROM "
                        + globalSchema
                        + ".tenant WHERE schema_nome IS NOT NULL ORDER BY schema_nome",
                String.class);
    }

    @Override
    public boolean tenantExists(UUID tenantId) {
        Integer found =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM " + globalSchema + ".tenant WHERE tenant_id = ?",
                        Integer.class,
                        tenantId);
        return found != null && found > 0;
    }
}
