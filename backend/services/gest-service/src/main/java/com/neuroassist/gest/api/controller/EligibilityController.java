package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.api.dto.EligibilityRequest;
import com.neuroassist.gest.api.dto.EligibilityResponse;
import com.neuroassist.gest.application.port.in.EligibilityUseCase;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Security: @PreAuthorize derived from rbac.arch.json via the module generator.
// To change which permission gates a method, edit rbac.arch.json and re-run
// be-module-generator.py; do not patch the annotation by hand.
@Slf4j
@RestController
@RequestMapping("/gest-service/eligibility")
@RequiredArgsConstructor
public class EligibilityController {

    private final EligibilityUseCase eligibilityUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('gest.eligibility.read')")
    public ResponseEntity<EligibilityResponse> check(
            @RequestBody @Valid EligibilityRequest request) {
        return ResponseEntity.ok(eligibilityUseCase.check(request));
    }
}
