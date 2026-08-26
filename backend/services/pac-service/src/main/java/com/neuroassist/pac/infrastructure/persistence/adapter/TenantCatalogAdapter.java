package com.neuroassist.pac.infrastructure.persistence.adapter;

import com.neuroassist.pac.application.port.out.TenantCatalogPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the tenant catalogue from the global schema.
 *
 * <p>Uses {@link JdbcTemplate} with a fully-qualified table name rather than JPA on purpose: a JPA
 * entity would be routed by {@code MultiTenantConnectionProvider} to whatever tenant is bound, and
 * this query exists precisely to run when none is — it is the query that decides which tenants to
 * bind.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantCatalogAdapter implements TenantCatalogPort {

    private static final String ACTIVE_TENANTS =
            "SELECT tenant_id::text FROM neuroassist_global.tenant WHERE status = 'ACTIVE'";

    private final JdbcTemplate jdbcTemplate;

    @Override
    public List<String> activeTenantIds() {
        try {
            return jdbcTemplate.queryForList(ACTIVE_TENANTS, String.class);
        } catch (Exception e) {
            // Background work must not die because the catalogue was briefly unreachable; the next
            // tick tries again.
            log.warn("Could not read the tenant catalogue: {}", e.getMessage());
            return List.of();
        }
    }
}
