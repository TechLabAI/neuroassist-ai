package com.neuroassist.conv.api.controller;

import com.neuroassist.conv.application.port.in.BindingResolutionUseCase;
import com.neuroassist.conv.shared.audit.AuditAction;
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
@RequestMapping("/conv-service/binding-resolution")
@RequiredArgsConstructor
public class BindingResolutionController {

    private final BindingResolutionUseCase bindingResolutionUseCase;

    @PutMapping("/{id}/resolveBinding")
    @PreAuthorize("hasAuthority('conv.binding-resolution.resolver')")
    @AuditAction("UPDATE_BINDING_RESOLUTION")
    public ResponseEntity<Void> resolveBinding() {
        // TODO[SCAFFOLD]: delegate to BindingResolutionUseCase
        throw new UnsupportedOperationException("resolveBinding not implemented");
    }
}
