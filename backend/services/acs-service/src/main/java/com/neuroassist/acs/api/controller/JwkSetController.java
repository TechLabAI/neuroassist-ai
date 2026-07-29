package com.neuroassist.acs.api.controller;

import com.neuroassist.acs.application.port.in.JwkSetUseCase;
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
@RequestMapping("/acs-service/jwk-set")
@RequiredArgsConstructor
public class JwkSetController {

    private final JwkSetUseCase jwkSetUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('acs.jwk-set.read')")
    public ResponseEntity<Void> getPublicKeys() {
        // TODO: delegate to JwkSetUseCase
        throw new UnsupportedOperationException("getPublicKeys not implemented");
    }
}
