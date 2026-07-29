package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.application.port.in.DirectoryUseCase;
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
@RequestMapping("/acs-service/directory")
@RequiredArgsConstructor
public class DirectoryController {

    private final DirectoryUseCase directoryUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.directory.read')")
    public ResponseEntity<Void> getTenantValidity() {
        // TODO: delegate to DirectoryUseCase
        throw new UnsupportedOperationException("getTenantValidity not implemented");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.directory.read')")
    public ResponseEntity<Void> getRoutingTargets() {
        // TODO: delegate to DirectoryUseCase
        throw new UnsupportedOperationException("getRoutingTargets not implemented");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.directory.read')")
    public ResponseEntity<Void> getCaregiverRole() {
        // TODO: delegate to DirectoryUseCase
        throw new UnsupportedOperationException("getCaregiverRole not implemented");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.directory.read')")
    public ResponseEntity<Void> getPatientScope() {
        // TODO: delegate to DirectoryUseCase
        throw new UnsupportedOperationException("getPatientScope not implemented");
    }
}
