package com.neuroassist.edge.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.neuroassist.edge.application.port.in.DeviceListUseCase;
import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.infrastructure.security.TenantIdentifierResolver;
import java.util.UUID;
import javax.sql.DataSource;
import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.TestConstructor;

/**
 * Cross-tenant isolation integration tests for EDGE (RULE-07).
 *
 * <p>Verifies that:
 *
 * <ol>
 *   <li>A device registered under tenant A is NOT visible when listed under tenant B, exercised
 *       through {@link DeviceListUseCase} and {@link DeviceRegistrationUseCase} — never via direct
 *       SQL (SQL would prove the schema, not the enforcement).
 *   <li>The same device IS visible when listed under the same tenant A (anti-vacuity guard).
 *   <li>Schema routing uses the {@code neuroassist_global.tenant} catalogue (UUID → {@code
 *       schema_nome}), not the fallback "use UUID as schema name". Both tenant UUIDs are
 *       intentionally different from their schema names ({@code
 *       00000000-0000-0000-0000-000000000001 ≠ t_emp_test}). A broken provider would try to {@code
 *       SET search_path TO "00000000-0000-0000-0000-000000000001"}, which does not exist — the test
 *       would throw instead of pass.
 *   <li>Operating without a bound tenant causes {@link TenantContextHolder#require()} to throw
 *       {@link IllegalStateException} (RULE-07), rather than silently querying global data.
 * </ol>
 *
 * <p>Both tenant schemas ({@code t_emp_test} and {@code t_emp_other}) are provisioned by {@code
 * init-schema.sql}; the catalogue rows are also seeded there so that the {@link
 * com.neuroassist.edge.infrastructure.security.MultiTenantConnectionProvider} exercises its real
 * lookup path.
 */
@TestConstructor(autowireMode = TestConstructor.AutowireMode.ALL)
@RequiredArgsConstructor
class MultiTenantIsolationIT extends BaseIntegrationTest {

    /**
     * Tenant A UUID → schema {@code t_emp_test}. UUID deliberately ≠ schema name so a broken
     * provider cannot pass via the identifier-as-schema fallback.
     */
    private static final String TENANT_A_UUID = "00000000-0000-0000-0000-000000000001";

    /**
     * Tenant B UUID → schema {@code t_emp_other}. UUID deliberately ≠ schema name — same reason.
     */
    private static final String TENANT_B_UUID = "00000000-0000-0000-0000-000000000002";

    private static final String ACTOR = "isolation-test-actor";

    private final DeviceRegistrationUseCase deviceRegistrationUseCase;
    private final DeviceListUseCase deviceListUseCase;
    private final TenantIdentifierResolver tenantIdentifierResolver;
    private final DataSource dataSource;

    @AfterEach
    void clearTenantContext() {
        TenantContextHolder.clear();
    }

    // -------------------------------------------------------------------------
    // Scenario 1 — data registered in tenant A is invisible from tenant B
    // -------------------------------------------------------------------------

    @Test
    void deviceRegisteredInTenantA_isNotVisibleFromTenantB() {
        TenantContextHolder.set(TENANT_A_UUID);
        DeviceRegistrationUseCase.RegisterResult registered =
                deviceRegistrationUseCase.register("isolation-device-a2b", null, ACTOR);
        UUID deviceId = registered.deviceId();

        TenantContextHolder.clear();
        TenantContextHolder.set(TENANT_B_UUID);
        Page<DeviceListUseCase.DeviceListResult> pageB =
                deviceListUseCase.listDevices(PageRequest.of(0, 1000));

        assertThat(pageB.getContent())
                .describedAs(
                        "Tenant B must not see device %s registered under tenant A — cross-tenant"
                                + " data isolation breach",
                        deviceId)
                .noneMatch(d -> d.deviceId().equals(deviceId));
    }

    // -------------------------------------------------------------------------
    // Scenario 2 — anti-vacuity: the same device IS visible from tenant A
    // (proves the test would actually detect an isolation failure, not just an
    // empty table for tenant B)
    // -------------------------------------------------------------------------

    @Test
    void deviceRegisteredInTenantA_isVisibleFromTenantA() {
        TenantContextHolder.set(TENANT_A_UUID);
        DeviceRegistrationUseCase.RegisterResult registered =
                deviceRegistrationUseCase.register("isolation-positive-a", null, ACTOR);
        UUID deviceId = registered.deviceId();

        TenantContextHolder.clear();
        TenantContextHolder.set(TENANT_A_UUID);
        Page<DeviceListUseCase.DeviceListResult> pageA =
                deviceListUseCase.listDevices(PageRequest.of(0, 1000));

        assertThat(pageA.getContent())
                .describedAs("Tenant A must see its own device %s in the listing", deviceId)
                .anyMatch(d -> d.deviceId().equals(deviceId));
    }

    // -------------------------------------------------------------------------
    // Scenario 3 — symmetry: tenant B data stays invisible to tenant A
    // -------------------------------------------------------------------------

    @Test
    void deviceRegisteredInTenantB_isNotVisibleFromTenantA() {
        TenantContextHolder.set(TENANT_B_UUID);
        DeviceRegistrationUseCase.RegisterResult registered =
                deviceRegistrationUseCase.register("isolation-device-b2a", null, ACTOR);
        UUID deviceId = registered.deviceId();

        TenantContextHolder.clear();
        TenantContextHolder.set(TENANT_A_UUID);
        Page<DeviceListUseCase.DeviceListResult> pageA =
                deviceListUseCase.listDevices(PageRequest.of(0, 1000));

        assertThat(pageA.getContent())
                .describedAs(
                        "Tenant A must not see device %s registered under tenant B — cross-tenant"
                                + " data isolation breach",
                        deviceId)
                .noneMatch(d -> d.deviceId().equals(deviceId));
    }

    // -------------------------------------------------------------------------
    // Scenario 4 — catalogue routing: UUID → schema_nome via neuroassist_global.tenant
    //
    // The UUID strings (e.g. 00000000-0000-0000-0000-000000000001) are not valid
    // Postgres schema names and do not exist in the database.  If
    // MultiTenantConnectionProvider fell back to using the identifier as the schema
    // name directly it would execute:
    //   SET search_path TO "00000000-0000-0000-0000-000000000001"
    // which Postgres rejects with "invalid schema name" (42P01/3F000) — the register()
    // call would throw instead of returning normally.  Successful completion proves the
    // provider looked up the catalogue and resolved the UUID to t_emp_test / t_emp_other.
    // -------------------------------------------------------------------------

    @Test
    void catalogueRouting_tenantA_uuidResolvesToSchema_notUsedAsSchemaName() {
        TenantContextHolder.set(TENANT_A_UUID);
        assertThatCode(() -> deviceRegistrationUseCase.register("catalogue-routing-a", null, ACTOR))
                .describedAs(
                        "register() with TENANT_A_UUID must succeed — UUID resolved to t_emp_test"
                                + " via catalogue, not treated as schema name")
                .doesNotThrowAnyException();
    }

    @Test
    void catalogueRouting_tenantB_uuidResolvesToSchema_notUsedAsSchemaName() {
        TenantContextHolder.set(TENANT_B_UUID);
        assertThatCode(() -> deviceRegistrationUseCase.register("catalogue-routing-b", null, ACTOR))
                .describedAs(
                        "register() with TENANT_B_UUID must succeed — UUID resolved to t_emp_other"
                                + " via catalogue, not treated as schema name")
                .doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------
    // Scenario 5 — negative path: no tenant bound → RULE-07 enforced
    // -------------------------------------------------------------------------

    @Test
    void listDevices_withoutTenantBound_throwsIllegalStateException() {
        TenantContextHolder.clear();

        assertThatThrownBy(() -> deviceListUseCase.listDevices(PageRequest.of(0, 1000)))
                .describedAs(
                        "DeviceListService.listDevices() must throw when no tenant is bound —"
                                + " TenantContextHolder.require() (RULE-07) must fire before any"
                                + " data access")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RULE-07");
    }

    @Test
    void registerDevice_withoutTenantBound_throwsIllegalStateException() {
        TenantContextHolder.clear();

        assertThatThrownBy(
                        () ->
                                deviceRegistrationUseCase.register(
                                        "should-never-persist", null, ACTOR))
                .describedAs(
                        "DeviceRegistrationService.register() must throw when no tenant is bound"
                                + " — TenantContextHolder.require() (RULE-07) must fire before any"
                                + " data access")
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("RULE-07");
    }

    // -------------------------------------------------------------------------
    // Scenario 6 — TenantIdentifierResolver contracts
    // -------------------------------------------------------------------------

    @Test
    void tenantIdentifierResolver_returnsTenantIdUnchanged_whenTenantSet() {
        TenantContextHolder.set(TENANT_A_UUID);
        String resolved = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        assertThat(resolved)
                .describedAs(
                        "TenantIdentifierResolver must pass the identifier through unchanged so"
                                + " MultiTenantConnectionProvider can map it via the catalogue")
                .isEqualTo(TENANT_A_UUID);
    }

    @Test
    void tenantIdentifierResolver_fallsBackToGlobal_whenNoTenantSet() {
        TenantContextHolder.clear();
        String resolved = tenantIdentifierResolver.resolveCurrentTenantIdentifier();

        assertThat(resolved)
                .describedAs(
                        "When no tenant is bound the resolver must return neuroassist_global so"
                                + " global-scoped queries (e.g. credential verification) can run")
                .isEqualTo("neuroassist_global");
    }

    // -------------------------------------------------------------------------
    // Scenario 7 — schema existence sanity (foundation of the 503 guard)
    // -------------------------------------------------------------------------

    @Test
    void schemaExistenceCheck_tenantASchemaExists() throws Exception {
        boolean exists = checkSchemaExists("t_emp_test");

        assertThat(exists)
                .describedAs(
                        "Schema t_emp_test must exist — it is the provisioned schema for tenant A"
                                + " and was created by init-schema.sql")
                .isTrue();
    }

    @Test
    void schemaExistenceCheck_tenantBSchemaExists() throws Exception {
        boolean exists = checkSchemaExists("t_emp_other");

        assertThat(exists)
                .describedAs(
                        "Schema t_emp_other must exist — it is the provisioned schema for tenant B"
                                + " and was created by init-schema.sql")
                .isTrue();
    }

    @Test
    void schemaExistenceCheck_unknownSchemaDoesNotExist() throws Exception {
        boolean exists = checkSchemaExists("t_nonexistent_" + UUID.randomUUID());

        assertThat(exists)
                .describedAs(
                        "A randomly named schema must not exist — the 503 guard would reject"
                                + " requests for unknown tenants")
                .isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean checkSchemaExists(String schemaName) throws Exception {
        try (var conn = dataSource.getConnection();
                var ps =
                        conn.prepareStatement(
                                "SELECT 1 FROM information_schema.schemata"
                                        + " WHERE schema_name = ?")) {
            ps.setString(1, schemaName);
            try (var rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }
}
