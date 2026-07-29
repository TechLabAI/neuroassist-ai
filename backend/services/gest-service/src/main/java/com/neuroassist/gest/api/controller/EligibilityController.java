package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.application.port.in.EligibilityUseCase;
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
@RequestMapping("/gest-service/eligibility")
@RequiredArgsConstructor
public class EligibilityController {

    private final EligibilityUseCase eligibilityUseCase;

    @PutMapping("/{id}/check")
    @PreAuthorize("hasAuthority('gest.eligibility.read')")
    public ResponseEntity<Void> check() {
        // TODO: delegate to EligibilityUseCase
        throw new UnsupportedOperationException("check not implemented");
    }
}
