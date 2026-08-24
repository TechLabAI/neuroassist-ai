package com.neuroassist.mig.infrastructure.flyway;

import com.neuroassist.mig.application.port.out.SchemaMigrationPort;
import com.neuroassist.mig.application.service.ProvisioningInProgressException;
import com.neuroassist.mig.domain.model.TenantSchemaProvisioning;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.output.MigrateResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Flyway adapter — the only place in the service that puts a schema name into DDL (E2E-016).
 *
 * <p>Programmatic Flyway, not the Spring Boot auto-configuration: the auto-configured instance is
 * bound to one set of schemas at startup, and this face has to target a different schema per
 * request. The two coexist — the deploy-time job keeps using the auto-configured run, and the
 * {@code api} profile turns that one off.
 */
@Slf4j
@Component
public class FlywaySchemaMigrationAdapter implements SchemaMigrationPort {

    /**
     * Namespace for the advisory lock. Postgres advisory locks share one global 64-bit space, so a
     * bare {@code hashCode} could collide with a lock taken by anything else on the same database.
     * Two keys (namespace, tenant hash) keep MIG's locks in their own space.
     */
    private static final int LOCK_NAMESPACE = 0x4d494752; // "MIGR"

    private final DataSource dataSource;
    private final JdbcTemplate jdbcTemplate;
    private final String locations;
    private final String globalLocations;
    private final String globalSchema;
    private final String histServiceRole;
    private final boolean outOfOrder;

    public FlywaySchemaMigrationAdapter(
            DataSource dataSource,
            @Value("${neuroassist.mig.tenant-locations:classpath:db/migration/empresa}")
                    String locations,
            @Value("${neuroassist.mig.global-locations:classpath:db/migration/global}")
                    String globalLocations,
            @Value("${neuroassist.mig.global-schema:neuroassist_global}") String globalSchema,
            @Value("${neuroassist.mig.hist-service-role:${HIST_SERVICE_ROLE:neuroassist}}")
                    String histServiceRole,
            @Value("${neuroassist.mig.out-of-order:true}") boolean outOfOrder) {
        this.dataSource = dataSource;
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.locations = locations;
        this.globalLocations = globalLocations;
        this.globalSchema = globalSchema;
        this.histServiceRole = histServiceRole;
        this.outOfOrder = outOfOrder;
    }

    @Override
    public boolean createSchemaIfAbsent(String schema) {
        guard(schema);
        if (schemaExists(schema)) {
            return false;
        }
        // Identifier, not a value — it cannot be bound. `guard` above is what makes this safe.
        jdbcTemplate.execute("CREATE SCHEMA IF NOT EXISTS \"" + schema + "\"");
        log.info("Created schema {}", schema);
        return true;
    }

    @Override
    public boolean schemaExists(String schema) {
        guard(schema);
        Integer found =
                jdbcTemplate.queryForObject(
                        "SELECT count(*) FROM information_schema.schemata WHERE schema_name = ?",
                        Integer.class,
                        schema);
        return found != null && found > 0;
    }

    @Override
    public List<String> migrateToHead(String schema) {
        guard(schema);
        MigrateResult result = flywayFor(schema).migrate();
        return result.migrations == null
                ? List.of()
                : result.migrations.stream().map(m -> m.version).filter(v -> v != null).toList();
    }

    @Override
    public String currentVersion(String schema) {
        guard(schema);
        MigrationInfo current = flywayFor(schema).info().current();
        return current == null || current.getVersion() == null
                ? null
                : current.getVersion().getVersion();
    }

    @Override
    public List<String> migrateGlobalToHead() {
        MigrateResult result = flywayFor(globalSchema, globalLocations).migrate();
        return result.migrations == null
                ? List.of()
                : result.migrations.stream().map(m -> m.version).filter(v -> v != null).toList();
    }

    @Override
    public String currentGlobalVersion() {
        MigrationInfo current = flywayFor(globalSchema, globalLocations).info().current();
        return current == null || current.getVersion() == null
                ? null
                : current.getVersion().getVersion();
    }

    /**
     * <b>Lock, work and unlock share one physical connection, deliberately.</b> A Postgres advisory
     * lock is owned by the <i>session</i>, so taking it through a pooled {@code JdbcTemplate} would
     * lock on whichever connection the pool handed out and release on whichever it handed out next
     * — different sessions. The lock would then leak on the first connection and the unlock would
     * silently no-op, so the second call for that tenant would answer 409 forever.
     */
    @Override
    public <T> T withTenantLock(UUID tenantId, Supplier<T> work) {
        int key = tenantId.hashCode();
        try (java.sql.Connection lockConnection = dataSource.getConnection()) {
            if (!tryLock(lockConnection, key)) {
                // pg_try_advisory_lock, never pg_advisory_lock: blocking would hold an HTTP thread
                // for as long as the other run takes, and acs-service waits synchronously on the
                // other end. Answering 409 immediately is information; a hung request is not.
                throw new ProvisioningInProgressException(
                        "provisioning already in progress for tenant " + tenantId);
            }
            try {
                return work.get();
            } finally {
                unlock(lockConnection, key);
            }
        } catch (java.sql.SQLException e) {
            throw new IllegalStateException("could not acquire the provisioning lock", e);
        }
    }

    private boolean tryLock(java.sql.Connection connection, int key) throws java.sql.SQLException {
        try (java.sql.PreparedStatement ps =
                connection.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            ps.setInt(1, LOCK_NAMESPACE);
            ps.setInt(2, key);
            try (java.sql.ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private void unlock(java.sql.Connection connection, int key) {
        try (java.sql.PreparedStatement ps =
                connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            ps.setInt(1, LOCK_NAMESPACE);
            ps.setInt(2, key);
            ps.executeQuery().close();
        } catch (java.sql.SQLException e) {
            // Closing the connection ends the session and drops the lock anyway; log rather than
            // mask whatever the work block was about to return or throw.
            log.warn("Failed to release the provisioning advisory lock: {}", e.getMessage());
        }
    }

    private Flyway flywayFor(String schema) {
        return flywayFor(schema, locations);
    }

    private Flyway flywayFor(String schema, String locationSet) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas(schema)
                .defaultSchema(schema)
                .locations(
                        Arrays.stream(locationSet.split(","))
                                .map(String::trim)
                                .toArray(String[]::new))
                .placeholders(
                        java.util.Map.of(
                                "schema", schema,
                                "hist_service_role", histServiceRole))
                // Same reason as the job path: the per-module V ranges (PAC V0200-V0299, GEST
                // V0400-V0499, …) make any new migration in a low range out of order by
                // construction against higher ranges already applied.
                .outOfOrder(outOfOrder)
                .validateOnMigrate(true)
                .baselineOnMigrate(false)
                .load();
    }

    /**
     * Defence in depth. The service layer validates before calling, but this adapter is the last
     * thing standing between an HTTP request and {@code CREATE SCHEMA}, and it should not depend on
     * a caller elsewhere in the codebase having done the right thing.
     */
    private void guard(String schema) {
        if (schema == null || !TenantSchemaProvisioning.VALID_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("invalid schema name: " + schema);
        }
    }
}
