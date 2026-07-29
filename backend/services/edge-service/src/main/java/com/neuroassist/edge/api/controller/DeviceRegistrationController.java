package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
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
@RequestMapping("/edge-service/device-registration")
@RequiredArgsConstructor
public class DeviceRegistrationController {

    private final DeviceRegistrationUseCase deviceRegistrationUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('edge.device-registration.criar')")
    @AuditAction("CREATE_DEVICE_REGISTRATION")
    public ResponseEntity<Void> register() {
        // TODO: delegate to DeviceRegistrationUseCase
        throw new UnsupportedOperationException("register not implemented");
    }

    @PostMapping("/enroll")
    @PreAuthorize("hasAuthority('edge.device-registration.criar')")
    @AuditAction("CREATE_DEVICE_REGISTRATION")
    public ResponseEntity<Void> enroll() {
        // TODO: delegate to DeviceRegistrationUseCase
        throw new UnsupportedOperationException("enroll not implemented");
    }

    @PutMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('edge.device-registration.aposentar')")
    @AuditAction("UPDATE_DEVICE_REGISTRATION")
    public ResponseEntity<Void> retire() {
        // TODO: delegate to DeviceRegistrationUseCase
        throw new UnsupportedOperationException("retire not implemented");
    }
}
