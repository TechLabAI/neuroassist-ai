package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.api.dto.DetectionContractRequest;
import com.neuroassist.gest.api.dto.DetectionContractResponse;
import com.neuroassist.gest.application.port.in.DetectionContractUseCase;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Security: @PreAuthorize derived from rbac.arch.json via the module generator.
// To change which permission gates a method, edit rbac.arch.json and re-run
// be-module-generator.py; do not patch the annotation by hand.
@Slf4j
@RestController
@RequestMapping("/gest-service/detection-contract")
@RequiredArgsConstructor
public class DetectionContractController {

    private final DetectionContractUseCase detectionContractUseCase;

    @GetMapping
    @PreAuthorize("hasAuthority('gest.detection-contract.read')")
    public ResponseEntity<Object> getContractDescriptor() {
        return ResponseEntity.ok(detectionContractUseCase.getContractDescriptor());
    }

    @PostMapping
    @PreAuthorize("hasAuthority('gest.detection-contract.validar')")
    public ResponseEntity<DetectionContractResponse> validateReport(
            @RequestBody DetectionContractRequest request) {
        return ResponseEntity.ok(detectionContractUseCase.validateReport(request));
    }
}
