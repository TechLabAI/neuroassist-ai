package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.application.port.in.LivenessUseCase;
import com.neuroassist.edge.shared.audit.AuditAction;
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
@RequestMapping("/edge-service/liveness")
@RequiredArgsConstructor
public class LivenessController {

    private final LivenessUseCase livenessUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('edge.liveness.read')")
    public ResponseEntity<Void> evaluate() {
        // TODO: delegate to LivenessUseCase
        throw new UnsupportedOperationException("evaluate not implemented");
    }

    @PutMapping("/{id}/resolveDeviceForPatient")
    @PreAuthorize("hasAuthority('edge.liveness.resolver')")
    @AuditAction("UPDATE_LIVENESS")
    public ResponseEntity<Void> resolveDeviceForPatient() {
        // TODO: delegate to LivenessUseCase
        throw new UnsupportedOperationException("resolveDeviceForPatient not implemented");
    }

    @PutMapping("/{id}/resolveTenant")
    @PreAuthorize("hasAuthority('edge.liveness.resolver')")
    @AuditAction("UPDATE_LIVENESS")
    public ResponseEntity<Void> resolveTenant() {
        // TODO: delegate to LivenessUseCase
        throw new UnsupportedOperationException("resolveTenant not implemented");
    }
}
