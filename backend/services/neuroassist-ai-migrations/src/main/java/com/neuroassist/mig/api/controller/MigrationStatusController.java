package com.neuroassist.mig.api.controller;

import com.neuroassist.mig.application.port.out.SchemaMigrationPort;
import com.neuroassist.mig.application.port.out.TenantCatalogPort;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal status surface (E2E-030, api-contract §1.2).
 *
 * <p>Answers "who is behind" without opening the database. That question had no answer at all until
 * now, and the way it surfaced was a sweeper in another service logging {@code relation
 * "evnt_hist_outbox" does not exist} once per tick — a symptom several layers away from the cause.
 */
@Slf4j
@RestController
@RequestMapping("/internal/migrations")
@RequiredArgsConstructor
public class MigrationStatusController {

    private final SchemaMigrationPort schemaMigrationPort;
    private final TenantCatalogPort tenantCatalogPort;

    @GetMapping("/status")
    // `migrations:admin` além de `migrations:internal` (api-contract §1.4): esta pergunta é de
    // OPERAÇÃO, não do acs-service. Guardada só por `migrations:internal`, o endpoint existiria
    // para uma authority que nenhum operador consegue obter — o token do `platform-ops` levou
    // 403 na primeira tentativa de usá-lo.
    @PreAuthorize("hasAnyAuthority('migrations:internal', 'migrations:admin')")
    public ResponseEntity<StatusResponse> status() {
        String globalVersion = schemaMigrationPort.currentGlobalVersion();

        List<SchemaStatus> tenants =
                tenantCatalogPort.allTenantSchemas().stream()
                        .map(
                                schema -> {
                                    if (!schemaMigrationPort.schemaExists(schema)) {
                                        // The registry names a schema that is not there. Reporting
                                        // it as "no version" would read like a fresh tenant.
                                        return new SchemaStatus(schema, null, "missing");
                                    }
                                    return new SchemaStatus(
                                            schema,
                                            schemaMigrationPort.currentVersion(schema),
                                            "present");
                                })
                        .toList();

        return ResponseEntity.ok(
                new StatusResponse(
                        new SchemaStatus("neuroassist_global", globalVersion, "present"), tenants));
    }

    /**
     * @param state {@code present} or {@code missing} — a schema the registry names but the
     *     database does not have is a distinct state from one that merely has no migrations yet
     */
    public record SchemaStatus(String schema, String currentVersion, String state) {}

    public record StatusResponse(SchemaStatus global, List<SchemaStatus> tenants) {}
}
