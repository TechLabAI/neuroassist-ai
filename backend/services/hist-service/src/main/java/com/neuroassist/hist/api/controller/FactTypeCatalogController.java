package com.neuroassist.hist.api.controller;

import com.neuroassist.hist.application.port.in.FactTypeCatalogUseCase;
import com.neuroassist.hist.shared.audit.AuditAction;
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
@RequestMapping("/hist-service/fact-type-catalog")
@RequiredArgsConstructor
public class FactTypeCatalogController {

    private final FactTypeCatalogUseCase factTypeCatalogUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('hist.fact-type-catalog.read')")
    public ResponseEntity<Void> listActive() {
        // TODO: delegate to FactTypeCatalogUseCase
        throw new UnsupportedOperationException("listActive not implemented");
    }

}
