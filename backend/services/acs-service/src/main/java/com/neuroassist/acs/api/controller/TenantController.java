package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.application.port.in.TenantUseCase;
import com.neuroassist.acs.shared.audit.AuditAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

// Security: @PreAuthorize derived from rbac.arch.json via the module generator.
// To change which permission gates a method, edit rbac.arch.json and re-run
// be-module-generator.py; do not patch the annotation by hand.
@Slf4j
@RestController
@RequestMapping("/acs-service/tenant")
@RequiredArgsConstructor
public class TenantController {

    private final TenantUseCase tenantUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('acs.tenant.provisionar')")
    @AuditAction("CREATE_TENANT")
    public ResponseEntity<Void> provision() {
        // TODO: delegate to TenantUseCase
        throw new UnsupportedOperationException("provision not implemented");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.tenant.read')")
    public ResponseEntity<Void> findById() {
        // TODO: delegate to TenantUseCase
        throw new UnsupportedOperationException("findById not implemented");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('acs.tenant.reprocessar')")
    public ResponseEntity<Void> retryProvisioning() {
        // TODO: delegate to TenantUseCase
        throw new UnsupportedOperationException("retryProvisioning not implemented");
    }
}
