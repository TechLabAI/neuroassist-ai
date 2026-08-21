package com.neuroassist.gest.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.neuroassist.gest.application.port.out.TipoGestoRepositoryPort;
import com.neuroassist.gest.domain.model.TipoGesto;
import com.neuroassist.gest.infrastructure.security.TenantContextHolder;
import com.neuroassist.gest.infrastructure.security.TenantIdentifierResolver;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestConstructor;

/**
 * Cross-tenant isolation integration tests (INT-GEST-005).
 *
 * <p>Verifies that:
 *
 * <ol>
 *   <li>Data saved with tenant A is NOT visible when queried with tenant B.
 *   <li>Data saved with tenant B is NOT visible when queried with tenant A.
 *   <li>Schema routing ({@link TenantIdentifierResolver}) resolves to the correct {@code t_<id>}
 *       schema.
 *   <li>A DataSource check correctly identifies whether a tenant's schema exists (foundation of the
 *       503 guard in {@link com.neuroassist.gest.infrastructure.security.TenantContextFilter}).
 * </ol>
 *
 * <p>Tables in {@code t_emp_test} and {@code t_emp_other} are created by {@code init-schema.sql}
 * (not by Hibernate DDL), ensuring true per-schema isolation via PostgreSQL search_path. Implements
 * P3 acceptance criterion: "dados de tenant A não aparecem em query de tenant B".
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class MultiTenantIsolationIT extends BaseIntegrationTest {

    private static final String TENANT_A = "emp_test";
    private static final String TENANT_B = "emp_other";

    private final TipoGestoRepositoryPort tipoGestoPort;

    private final TenantIdentifierResolver tenantIdentifierResolver;

    private final DataSource dataSource;

    @AfterEach
    void clearTenant() {
        TenantContextHolder.clear();
    }

    @Test
    void tenantA_data_notVisibleFrom_tenantB() {
        // Insert data as tenant A
        TenantContextHolder.set(TENANT_A);
        TipoGesto gestureA =
                tipoGestoPort.save(TipoGesto.register("Gesto Exclusivo Tenant A", TENANT_A));
        UUID idA = gestureA.id();

        // Switch to tenant B and query
        TenantContextHolder.clear();
        TenantContextHolder.set(TENANT_B);
        Optional<TipoGesto> fromB = tipoGestoPort.findById(idA);

        assertThat(fromB)
                .describedAs("Tenant B must not see tenant A's data (cross-tenant isolation)")
                .isEmpty();
    }

    @Test
    void tenantB_data_notVisibleFrom_tenantA() {
        // Insert data as tenant B
        TenantContextHolder.set(TENANT_B);
        TipoGesto gestureB =
                tipoGestoPort.save(TipoGesto.register("Gesto Exclusivo Tenant B", TENANT_B));
        UUID idB = gestureB.id();

        // Switch to tenant A and query
        TenantContextHolder.clear();
        TenantContextHolder.set(TENANT_A);
        Optional<TipoGesto> fromA = tipoGestoPort.findById(idB);

        assertThat(fromA)
                .describedAs("Tenant A must not see tenant B's data (cross-tenant isolation)")
                .isEmpty();
    }

    @Test
    void findAll_forTenantA_doesNotIncludeTenantBItems() {
        // Insert one item per tenant
        TenantContextHolder.set(TENANT_A);
        tipoGestoPort.save(TipoGesto.register("Somente Tenant A", TENANT_A));

        TenantContextHolder.clear();
        TenantContextHolder.set(TENANT_B);
        tipoGestoPort.save(TipoGesto.register("Somente Tenant B", TENANT_B));

        // List from tenant A's perspective
        TenantContextHolder.clear();
        TenantContextHolder.set(TENANT_A);
        List<TipoGesto> allFromA = tipoGestoPort.findAllByTenant();

        assertThat(allFromA)
                .describedAs("Tenant A findAll must not include items from tenant B")
                .noneMatch(g -> g.nome().equals("Somente Tenant B"));
        assertThat(allFromA).anyMatch(g -> g.nome().equals("Somente Tenant A"));
    }

    /**
     * The resolver returns the tenant identifier untouched.
     *
     * <p>It used to prepend {@code t_} here. That hid the real identifier from
     * {@link com.neuroassist.gest.infrastructure.security.MultiTenantConnectionProvider}, which
     * resolves the schema from {@code neuroassist_global.tenant.schema_nome} — acs-service issues
     * the tenant UUID while the provisioned schema is e.g. {@code t_emp_dev}, so a prefixed
     * identifier never matched the catalogue and every ACS-issued tenant routed to a schema that
     * does not exist (E2E-009 / C-15). Mapping identifier → schema now lives solely in the
     * provider, which falls back to {@code t_<tenant>} when the catalogue has no row.
     */
    @Test
    void tenantIdentifierResolver_returnsIdentifierUnchanged() {
        TenantContextHolder.set(TENANT_A);
        String resolved = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        assertThat(resolved).isEqualTo(TENANT_A);
    }

    @Test
    void tenantIdentifierResolver_fallsBackToGlobal_whenNoTenant() {
        // No tenant set
        TenantContextHolder.clear();
        String resolved = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        assertThat(resolved).isEqualTo("neuroassist_global");
    }

    /**
     * Verifies the schema existence check that underpins the 503 guard in TenantContextFilter.
     * t_emp_test must exist (created by init-schema.sql); t_nonexistent must not. The filter uses
     * this check before binding TenantContextHolder.
     *
     * <p>Note: full HTTP-level 503 testing requires a valid JWT (P5 security setup). This test
     * validates the JDBC schema-check mechanism directly.
     */
    @Test
    void schemaExistenceCheck_knownSchemaExists() throws Exception {
        boolean exists = checkSchemaExists("t_emp_test");
        assertThat(exists)
                .describedAs("Schema t_emp_test must exist (created by init-schema.sql)")
                .isTrue();
    }

    @Test
    void schemaExistenceCheck_unknownSchemaDoesNotExist() throws Exception {
        boolean exists = checkSchemaExists("t_nonexistent_tenant_" + UUID.randomUUID());
        assertThat(exists)
                .describedAs("Non-existent schema must not be found — 503 guard would trigger")
                .isFalse();
    }

    private boolean checkSchemaExists(String schemaName) throws Exception {
        try (var conn = dataSource.getConnection();
                var ps =
                        conn.prepareStatement(
                                "SELECT 1 FROM information_schema.schemata WHERE schema_name = ?")) {
            ps.setString(1, schemaName);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
