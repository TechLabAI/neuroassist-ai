package com.neuroassist.mig.application.service;

import com.neuroassist.mig.application.port.in.MigrateAllUseCase;
import com.neuroassist.mig.application.port.out.SchemaMigrationPort;
import com.neuroassist.mig.application.port.out.TenantCatalogPort;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Fan-out of migrations across the global schema and every tenant schema (E2E-030, plan-MIG P2).
 *
 * <p>The gap this closes was created by making provisioning real: a tenant schema is built from the
 * template as of its provisioning moment, and nothing revisited it. Four tenants in the dev
 * environment sat without {@code evnt_hist_outbox} the day after the migration that adds it landed,
 * and the only symptom was a sweeper warning nobody was watching.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MigrateAllService implements MigrateAllUseCase {

    private final SchemaMigrationPort schemaMigrationPort;
    private final TenantCatalogPort tenantCatalogPort;

    @Override
    public RunReport migrateGlobal() {
        List<SchemaOutcome> outcomes = new ArrayList<>();
        outcomes.add(migrateGlobalSchema());
        return new RunReport(outcomes, null);
    }

    @Override
    public RunReport migrateAll() {
        List<SchemaOutcome> outcomes = new ArrayList<>();

        SchemaOutcome global = migrateGlobalSchema();
        outcomes.add(global);
        if (global.error() != null) {
            // The tenant template may reference the global registry, and the registry is what
            // tells us which tenants exist. Fanning out over a global schema that just failed to
            // migrate would be reading a catalogue whose shape is in doubt.
            log.error("Global schema failed — not fanning out to tenants");
            return new RunReport(outcomes, null);
        }

        List<String> schemas;
        try {
            schemas = tenantCatalogPort.allTenantSchemas();
        } catch (Exception e) {
            // Nothing was attempted per tenant, so nothing is half-applied. That is a different
            // failure from a migration error, and the deploy pipeline treats it differently.
            log.error("Pre-flight failed — tenant registry unreadable: {}", e.getMessage());
            return new RunReport(outcomes, "tenant registry unreadable: " + e.getMessage());
        }

        log.info("Fanning out to {} tenant schema(s)", schemas.size());
        for (String schema : schemas) {
            outcomes.add(migrateTenantSchema(schema));
        }

        List<SchemaOutcome> failures = outcomes.stream().filter(o -> o.error() != null).toList();
        if (failures.isEmpty()) {
            log.info("All {} schema(s) converged", outcomes.size());
        } else {
            // Naming them is the point: "2 of 9 failed" without saying which leaves an operator
            // opening the database to find out, which is exactly what this run report replaces.
            log.error(
                    "{} of {} schema(s) did NOT converge — the fleet is at mixed versions: {}",
                    failures.size(),
                    outcomes.size(),
                    failures.stream().map(SchemaOutcome::schema).toList());
        }
        return new RunReport(outcomes, null);
    }

    private SchemaOutcome migrateGlobalSchema() {
        try {
            List<String> applied = schemaMigrationPort.migrateGlobalToHead();
            String current = schemaMigrationPort.currentGlobalVersion();
            log.info("global schema converged: applied={} version={}", applied.size(), current);
            return SchemaOutcome.converged("neuroassist_global", applied, current);
        } catch (Exception e) {
            log.error("global schema FAILED: {}", e.getMessage());
            return SchemaOutcome.failed("neuroassist_global", e.getMessage());
        }
    }

    private SchemaOutcome migrateTenantSchema(String schema) {
        try {
            List<String> applied = schemaMigrationPort.migrateToHead(schema);
            String current = schemaMigrationPort.currentVersion(schema);
            log.info("{} converged: applied={} version={}", schema, applied.size(), current);
            return SchemaOutcome.converged(schema, applied, current);
        } catch (Exception e) {
            // Caught per schema on purpose: one company with a broken schema must not strand every
            // other company on an old version. The run still reports failure as a whole.
            log.error("{} FAILED: {}", schema, e.getMessage());
            return SchemaOutcome.failed(schema, e.getMessage());
        }
    }
}
