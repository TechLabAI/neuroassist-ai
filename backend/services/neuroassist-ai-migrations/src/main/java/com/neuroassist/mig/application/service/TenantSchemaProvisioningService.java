package com.neuroassist.mig.application.service;

import com.neuroassist.mig.application.port.in.ProvisionTenantSchemaUseCase;
import com.neuroassist.mig.application.port.out.SchemaMigrationPort;
import com.neuroassist.mig.application.port.out.TenantCatalogPort;
import com.neuroassist.mig.domain.model.ProvisioningStatus;
import com.neuroassist.mig.domain.model.TenantSchemaProvisioning;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Provisions a tenant schema for real (E2E-016).
 *
 * <p>Until now {@code MigProvisioningAdapter} in acs-service answered {@code PENDING} and then
 * {@code READY} from two hard-coded literals. Creating a tenant produced a row in {@code
 * neuroassist_global.tenant} with a {@code schema_nome} pointing at a schema that did not exist,
 * and acs-service flipped it to ACTIVE. Every one of the six E2E sessions ran against the seeded
 * {@code t_emp_dev}, so nothing ever exercised the path — which is the difference between "the
 * platform is multi-tenant" and "the platform has one tenant".
 *
 * <p><b>No {@code @Transactional}.</b> Flyway manages its own transactions per migration, and DDL
 * plus a schema-scoped history table do not belong inside a caller's transaction — a rollback would
 * leave the history disagreeing with the schema. Concurrency is handled by the advisory lock
 * (plan-MIG R5), not by isolation level.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantSchemaProvisioningService implements ProvisionTenantSchemaUseCase {

    private final SchemaMigrationPort schemaMigrationPort;
    private final TenantCatalogPort tenantCatalogPort;

    @Override
    public TenantSchemaProvisioning provision(UUID tenantId, String schema) {
        // Before anything else: a schema name is concatenated into DDL further down, and this
        // service holds the most privileged role in the platform.
        TenantSchemaProvisioning.requireValid(tenantId, schema);

        if (!tenantCatalogPort.tenantExists(tenantId)) {
            // Fail closed. Provisioning a schema for an unregistered tenant would create a
            // perfectly migrated schema that no service can ever route to, and the only trace
            // would be a schema nobody can attribute.
            throw new UnknownTenantException("tenant not in the global registry: " + tenantId);
        }

        return schemaMigrationPort.withTenantLock(
                tenantId,
                () -> {
                    boolean created = schemaMigrationPort.createSchemaIfAbsent(schema);
                    List<String> applied;
                    try {
                        applied = schemaMigrationPort.migrateToHead(schema);
                    } catch (Exception e) {
                        log.error(
                                "Provisioning FAILED tenant={} schema={} created={}: {}",
                                tenantId,
                                schema,
                                created,
                                e.getMessage());
                        throw new SchemaMigrationFailedException(
                                "migration failed for schema " + schema, e);
                    }

                    String current = schemaMigrationPort.currentVersion(schema);
                    log.info(
                            "Provisioned tenant={} schema={} created={} applied={} version={}",
                            tenantId,
                            schema,
                            created,
                            applied.size(),
                            current);

                    return new TenantSchemaProvisioning(
                            tenantId, schema, ProvisioningStatus.READY, applied, current);
                });
    }

    @Override
    public ProvisioningStatus statusOf(UUID tenantId) {
        if (!tenantCatalogPort.tenantExists(tenantId)) {
            throw new UnknownTenantException("tenant not in the global registry: " + tenantId);
        }

        Optional<String> schema = tenantCatalogPort.schemaOf(tenantId);
        if (schema.isEmpty()) {
            // acs-service records the schema name only after this service confirms it, so a null
            // here means provisioning has not completed — not that it failed.
            return ProvisioningStatus.PENDING;
        }

        if (!schemaMigrationPort.schemaExists(schema.get())) {
            // The registry names a schema that is not there. This is precisely the state the stub
            // used to manufacture on every onboarding, and it must never read as READY.
            log.warn("Tenant {} points at schema {} which does not exist", tenantId, schema.get());
            return ProvisioningStatus.FAILED;
        }

        return schemaMigrationPort.currentVersion(schema.get()) == null
                ? ProvisioningStatus.PROVISIONING
                : ProvisioningStatus.READY;
    }
}
