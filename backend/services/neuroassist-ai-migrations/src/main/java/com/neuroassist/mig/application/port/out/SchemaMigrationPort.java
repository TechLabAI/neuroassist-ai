package com.neuroassist.mig.application.port.out;

import java.util.List;

/**
 * Outbound port — the Flyway side of provisioning.
 *
 * <p>Split from the use case so the orchestration (validate, lock, create, migrate) can be tested
 * without a database, and so the one place that concatenates a schema name into DDL is a single
 * adapter rather than scattered through the service.
 */
public interface SchemaMigrationPort {

    /** {@code CREATE SCHEMA IF NOT EXISTS}. Returns true when the schema was actually created. */
    boolean createSchemaIfAbsent(String schema);

    boolean schemaExists(String schema);

    /**
     * Applies the tenant template to head.
     *
     * @return the versions applied by this call — empty when already at head
     */
    List<String> migrateToHead(String schema);

    /** Current applied version, or null when the schema has no Flyway history yet. */
    String currentVersion(String schema);

    /**
     * Applies the <b>global</b> migration set to the global schema (E2E-030).
     *
     * <p>Separate method rather than a locations parameter: the two sets are not interchangeable,
     * and a caller that could pass either would eventually pass the tenant template at the global
     * schema. Naming them apart makes that unrepresentable.
     *
     * @return the versions applied by this call — empty when already at head
     */
    List<String> migrateGlobalToHead();

    /** Current applied version of the global schema, or null when it has no history. */
    String currentGlobalVersion();

    /**
     * Runs {@code work} while holding a Postgres advisory lock keyed on the tenant.
     *
     * <p>plan-MIG R5: two concurrent provisioning calls for the same tenant would otherwise race
     * inside Flyway's own history table. The lock is per tenant, never global — one tenant being
     * provisioned must not stall every other tenant's onboarding.
     *
     * @return the result of {@code work}
     * @throws com.neuroassist.mig.application.service.ProvisioningInProgressException when the lock
     *     is already held
     */
    <T> T withTenantLock(java.util.UUID tenantId, java.util.function.Supplier<T> work);
}
