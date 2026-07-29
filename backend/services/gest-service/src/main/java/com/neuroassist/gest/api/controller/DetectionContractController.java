package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.application.port.in.DetectionContractUseCase;
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
@RequestMapping("/gest-service/detection-contract")
@RequiredArgsConstructor
public class DetectionContractController {

    private final DetectionContractUseCase detectionContractUseCase;

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gest.detection-contract.read')")
    public ResponseEntity<Void> getContractDescriptor() {
        // TODO: delegate to DetectionContractUseCase
        throw new UnsupportedOperationException("getContractDescriptor not implemented");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('gest.detection-contract.read')")
    public ResponseEntity<Void> validateReport() {
        // TODO: delegate to DetectionContractUseCase
        throw new UnsupportedOperationException("validateReport not implemented");
    }
}
