package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.api.dto.TenantRequest;
import com.neuroassist.acs.api.dto.TenantResponse;
import com.neuroassist.acs.application.port.in.TenantUseCase;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tenant administration endpoints (ACS-013).
 *
 * <p>POST /api/v1/acs/tenants — create + provision (ROLE_ADMIN or acs.tenant.create) GET
 * /api/v1/acs/tenants/{id} — get tenant by ID (acs.tenant.read) POST /api/v1/acs/tenants/{id}/retry
 * — retry stuck provisioning (acs.tenant.create)
 *
 * <p>RULE-04: no infrastructure dependency here.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/acs/tenants")
@RequiredArgsConstructor
public class TenantController {

    private final TenantUseCase tenantUseCase;

    /**
     * Creates and provisions a new tenant (triggers MIG SAGA).
     *
     * @param request tenant creation data
     * @return 201 TenantResponse (ACTIVE) | 202 TenantResponse (PROVISIONING — MIG async) | 400
     *     validation | 403 insufficient authority
     */
    @PostMapping
    @PreAuthorize("hasAuthority('acs.tenant.provisionar')")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantRequest request) {
        log.info("POST /api/v1/acs/tenants nome={}", request.nome());
        TenantResponse response = tenantUseCase.createTenant(request);
        // 202 Accepted when MIG was unavailable and tenant is pending async provisioning
        HttpStatus httpStatus =
                "PROVISIONING".equalsIgnoreCase(response.status())
                        ? HttpStatus.ACCEPTED
                        : HttpStatus.CREATED;
        return ResponseEntity.status(httpStatus).body(response);
    }

    /**
     * Retrieves a tenant by UUID.
     *
     * @param id the tenant UUID
     * @return 200 TenantResponse | 404 tenant-not-found
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.tenant.read')")
    public ResponseEntity<TenantResponse> findById(@PathVariable UUID id) {
        log.debug("GET /api/v1/acs/tenants/{}", id);
        TenantResponse response = tenantUseCase.findById(id);
        return ResponseEntity.ok(response);
    }

    /**
     * Retries tenant provisioning (for tenants stuck in PROVISIONING status).
     *
     * @param id the tenant UUID
     * @return 200 TenantResponse (updated) | 404 tenant-not-found | 502 MIG unavailable
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAuthority('acs.tenant.reprocessar')")
    public ResponseEntity<TenantResponse> retryProvisioning(@PathVariable UUID id) {
        log.info("POST /api/v1/acs/tenants/{}/retry", id);
        TenantResponse response = tenantUseCase.retryProvisioning(id);
        return ResponseEntity.ok(response);
    }
}
