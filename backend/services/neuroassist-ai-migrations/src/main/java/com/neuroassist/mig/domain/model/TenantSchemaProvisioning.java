package com.neuroassist.mig.domain.model;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Result of provisioning one tenant schema (E2E-016 / ADR-005).
 *
 * <p>RULE-01/RULE-02: zero Spring, zero JPA.
 *
 * @param tenantId the tenant this schema belongs to
 * @param schema the schema name, as owned and supplied by acs-service
 * @param status vocabulary shared with acs-service's {@code MigProvisioningPort}
 * @param appliedVersions migrations applied by this call — empty when already at head
 * @param currentVersion the schema version after the call
 */
public record TenantSchemaProvisioning(
        UUID tenantId,
        String schema,
        ProvisioningStatus status,
        List<String> appliedVersions,
        String currentVersion) {

    /**
     * A schema name cannot be a bound JDBC parameter — it is concatenated into DDL. This service
     * holds the most privileged database role in the platform, so the name it accepts from the
     * network is constrained to exactly the shape acs-service produces ({@code "t_" +} a UUID
     * without dashes) plus the hand-made dev schemas ({@code t_emp_dev}).
     *
     * <p>Rejecting rather than quoting is deliberate: quoting makes {@code "t_x"; DROP SCHEMA …}
     * inert but still lets an unexpected name create a schema nobody asked for.
     */
    public static final Pattern VALID_SCHEMA = Pattern.compile("^t_[a-z0-9_]{1,60}$");

    public TenantSchemaProvisioning {
        requireValid(tenantId, schema);
        appliedVersions = appliedVersions == null ? List.of() : List.copyOf(appliedVersions);
    }

    /**
     * Same check the constructor applies, callable before any DDL runs.
     *
     * <p>Validating only when the result record is built would put the guard <i>after</i> the
     * schema had already been created.
     */
    public static void requireValid(UUID tenantId, String schema) {
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (schema == null || !VALID_SCHEMA.matcher(schema).matches()) {
            throw new IllegalArgumentException("invalid schema name: " + schema);
        }
    }

    /** True when this call changed nothing because the schema was already at head. */
    public boolean alreadyCurrent() {
        return appliedVersions.isEmpty();
    }
}
