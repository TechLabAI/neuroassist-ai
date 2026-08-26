package com.neuroassist.mig.api.controller;

import com.neuroassist.mig.api.dto.ProvisionTenantRequest;
import com.neuroassist.mig.api.dto.ProvisionTenantResponse;
import com.neuroassist.mig.api.dto.TenantSchemaStatusResponse;
import com.neuroassist.mig.application.port.in.ProvisionTenantSchemaUseCase;
import com.neuroassist.mig.domain.model.ProvisioningStatus;
import com.neuroassist.mig.domain.model.TenantSchemaProvisioning;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Internal HTTP face for tenant provisioning (E2E-016, api-contract §1.1, ADR-005).
 *
 * <p><b>Internal-only.</b> Off the public {@code /api/**} edge, never routed by GWY, no published
 * OpenAPI. The only legitimate caller is acs-service during empresa onboarding.
 *
 * <p>RULE-04: no dependency on {@code infrastructure.*}.
 */
@Slf4j
@RestController
@RequestMapping("/internal/migrations/tenants")
@RequiredArgsConstructor
public class TenantProvisioningController {

    private final ProvisionTenantSchemaUseCase provisionTenantSchemaUseCase;

    /**
     * Creates the tenant schema if absent and migrates it to head. Idempotent — re-invoking a
     * tenant already at head is a no-op success, so a retry after a timeout is safe.
     *
     * <p>200, never 201: the caller cannot distinguish "created now" from "already existed" by
     * status code, and should not need to. {@code appliedVersions} carries that detail for logs.
     */
    @PutMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('migrations:internal')")
    public ResponseEntity<ProvisionTenantResponse> provision(
            @PathVariable UUID tenantId, @RequestBody ProvisionTenantRequest request) {

        log.info("Provisioning request tenant={} schema={}", tenantId, request.schema());
        TenantSchemaProvisioning result =
                provisionTenantSchemaUseCase.provision(tenantId, request.schema());

        return ResponseEntity.ok(
                new ProvisionTenantResponse(
                        result.tenantId(),
                        result.schema(),
                        result.status().name(),
                        result.appliedVersions(),
                        result.currentVersion()));
    }

    /**
     * Provisioning status, for acs-service's poll path.
     *
     * <p>That path stays dormant while provisioning is synchronous — which is the reason to keep
     * this honest rather than convenient. The stub it replaces answered a hard-coded {@code READY},
     * and a status endpoint that always says READY is indistinguishable from one that works.
     */
    @GetMapping("/{tenantId}")
    @PreAuthorize("hasAuthority('migrations:internal')")
    public ResponseEntity<TenantSchemaStatusResponse> status(@PathVariable UUID tenantId) {
        ProvisioningStatus status = provisionTenantSchemaUseCase.statusOf(tenantId);
        return ResponseEntity.ok(
                new TenantSchemaStatusResponse(
                        tenantId,
                        status.name(),
                        status == ProvisioningStatus.READY ? 100 : 0,
                        null));
    }
}
