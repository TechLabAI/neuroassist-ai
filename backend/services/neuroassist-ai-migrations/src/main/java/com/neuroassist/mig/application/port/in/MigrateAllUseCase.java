package com.neuroassist.mig.application.port.in;

import java.util.List;

/**
 * Inbound port — converge every schema of the platform to head (E2E-030, plan-MIG P2).
 *
 * <p>Exists because provisioning a tenant applies the template <i>as of that moment</i> and nothing
 * revisited it afterwards. While provisioning was a stub there was exactly one tenant schema, and a
 * job with a hard-coded {@code FLYWAY_SCHEMAS} was enough. The moment creating companies started
 * working, that job became the bottleneck: it knows one schema and the platform has N.
 */
public interface MigrateAllUseCase {

    /**
     * Migrates the global schema first, then every tenant schema in the registry.
     *
     * <p><b>Global before tenants, always.</b> The tenant template is allowed to reference the
     * global registry; the reverse is not. Running them in the other order would work most of the
     * time, which is worse than never working.
     *
     * <p><b>One schema's failure never stops the others.</b> A single tenant with a broken schema
     * must not strand every other company on an old version — but the run as a whole then reports
     * failure, because a fleet at mixed versions is not a fleet that should be rolled out to.
     */
    RunReport migrateAll();

    /** Migrates the global schema only. */
    RunReport migrateGlobal();

    /**
     * @param outcomes one entry per schema visited, in visit order
     * @param preflightError set when the run could not start at all — the registry was unreadable
     *     or the database unreachable. Distinct from a migration failure because the remedy is
     *     different: nothing was attempted, so nothing is half-applied.
     */
    record RunReport(List<SchemaOutcome> outcomes, String preflightError) {

        public boolean preflightFailed() {
            return preflightError != null;
        }

        public boolean anyFailed() {
            return outcomes.stream().anyMatch(o -> o.error() != null);
        }

        public List<SchemaOutcome> failures() {
            return outcomes.stream().filter(o -> o.error() != null).toList();
        }
    }

    /**
     * @param appliedVersions empty when the schema was already at head
     * @param error null when the schema converged
     */
    record SchemaOutcome(
            String schema, List<String> appliedVersions, String currentVersion, String error) {

        public static SchemaOutcome converged(
                String schema, List<String> applied, String currentVersion) {
            return new SchemaOutcome(schema, applied, currentVersion, null);
        }

        public static SchemaOutcome failed(String schema, String error) {
            return new SchemaOutcome(schema, List.of(), null, error);
        }
    }
}
