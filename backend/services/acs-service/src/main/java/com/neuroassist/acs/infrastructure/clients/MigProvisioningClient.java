package com.neuroassist.acs.infrastructure.clients;

import java.util.List;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * E2E-016 — Feign client for the MIG internal provisioning API (api-contract §1.1, ADR-005).
 *
 * <p>RULE-06: {@code @FeignClient} only in {@code infrastructure.clients}.
 *
 * <p><b>Both sides of this contract were written together</b>, and the producer ({@code
 * TenantProvisioningController} in neuroassist-ai-migrations) is the authority. That matters more
 * than usual here, because the previous shape of this file — an empty interface with {@code //
 * TODO: add methods from techlab-ai-integration.md} — meant every onboarding recorded a schema name
 * for a schema that was never created.
 *
 * <p><b>The schema name travels in the request body.</b> acs-service mints it ({@code
 * TenantProvisioningService}) and records it in {@code neuroassist_global.tenant}; MIG applies it.
 * Neither side derives it from the tenant id — see CLAUDE.md, "tenant id ≠ schema name".
 *
 * <p><b>Service identity, tenant-less.</b> {@link MigInternalFeignConfig} attaches acs-service's
 * own {@code migrations:internal} token. It carries no tenant claim on purpose: the tenant being
 * provisioned has no schema yet, so there is nothing to route to.
 */
@FeignClient(
        name = "neuroassist-ai-migrations",
        url = "${neuroassist.mig.url:${MIG_SERVICE_URL:http://migrations-api:8091}}",
        configuration = MigInternalFeignConfig.class)
public interface MigProvisioningClient {

    /**
     * Creates the tenant schema if absent and migrates it to head. Idempotent on the MIG side, so a
     * retry after a timeout cannot double-provision.
     */
    @PutMapping("/internal/migrations/tenants/{tenantId}")
    ProvisionTenantResponse requestSchema(
            @PathVariable("tenantId") UUID tenantId, @RequestBody ProvisionTenantRequest request);

    /** Provisioning status, for the poll path. */
    @GetMapping("/internal/migrations/tenants/{tenantId}")
    TenantSchemaStatusResponse getSchemaStatus(@PathVariable("tenantId") UUID tenantId);

    /**
     * {@code public} is load-bearing: a package-private record is invisible to the JDK dynamic
     * proxy Feign builds ({@code jdk.proxy3.$ProxyN}) and fails at runtime, not at compile time.
     */
    public record ProvisionTenantRequest(String schema) {}

    /** Transcribed from the producer's {@code ProvisionTenantResponse}, field for field. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record ProvisionTenantResponse(
            UUID tenantId,
            String schema,
            String status,
            List<String> appliedVersions,
            String currentVersion) {}

    /** Transcribed from the producer's {@code TenantSchemaStatusResponse}. */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record TenantSchemaStatusResponse(
            UUID tenantId, String status, int percentual, Integer estimatedSeconds) {}
}
