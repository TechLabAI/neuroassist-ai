package com.neuroassist.pac.api.controller;

import com.neuroassist.pac.application.port.in.ComandoLifecycleUseCase;
import com.neuroassist.pac.shared.audit.AuditAction;
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
@RequestMapping("/pac-service/comando-lifecycle")
@RequiredArgsConstructor
public class ComandoLifecycleController {

    private final ComandoLifecycleUseCase comandoLifecycleUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.read')")
    public ResponseEntity<Void> validate() {
        // TODO: delegate to ComandoLifecycleUseCase
        throw new UnsupportedOperationException("validate not implemented");
    }

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.publicar')")
    @AuditAction("UPDATE_COMANDO_LIFECYCLE")
    public ResponseEntity<Void> publish() {
        // TODO: delegate to ComandoLifecycleUseCase
        throw new UnsupportedOperationException("publish not implemented");
    }

    @PutMapping("/{id}/recalibrate")
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.recalibrar')")
    @AuditAction("UPDATE_COMANDO_LIFECYCLE")
    public ResponseEntity<Void> recalibrate() {
        // TODO: delegate to ComandoLifecycleUseCase
        throw new UnsupportedOperationException("recalibrate not implemented");
    }

    @PutMapping("/{id}/retire")
    @PreAuthorize("hasAuthority('pac.comando-lifecycle.aposentar')")
    @AuditAction("UPDATE_COMANDO_LIFECYCLE")
    public ResponseEntity<Void> retire() {
        // TODO: delegate to ComandoLifecycleUseCase
        throw new UnsupportedOperationException("retire not implemented");
    }
}
