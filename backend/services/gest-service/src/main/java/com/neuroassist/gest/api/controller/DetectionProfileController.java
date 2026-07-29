package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.shared.audit.AuditAction;
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
@RequestMapping("/gest-service/detection-profile")
@RequiredArgsConstructor
public class DetectionProfileController {

    private final DetectionProfileUseCase detectionProfileUseCase;

    @PutMapping("/{id}/publish")
    @PreAuthorize("hasAuthority('gest.detection-profile.publicar')")
    @AuditAction("UPDATE_DETECTION_PROFILE")
    public ResponseEntity<Void> publish() {
        // TODO: delegate to DetectionProfileUseCase
        throw new UnsupportedOperationException("publish not implemented");
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<Void> getActive() {
        // TODO: delegate to DetectionProfileUseCase
        throw new UnsupportedOperationException("getActive not implemented");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<Void> findByVersion() {
        // TODO: delegate to DetectionProfileUseCase
        throw new UnsupportedOperationException("findByVersion not implemented");
    }

    @GetMapping
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<Void> pullByDevice() {
        // TODO: delegate to DetectionProfileUseCase
        throw new UnsupportedOperationException("pullByDevice not implemented");
    }
}
