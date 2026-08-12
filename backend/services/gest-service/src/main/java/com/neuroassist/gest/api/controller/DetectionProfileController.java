package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.shared.audit.AuditAction;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Security: @PreAuthorize derived from rbac.arch.json via the module generator.
// To change which permission gates a method, edit rbac.arch.json and re-run
// be-module-generator.py; do not patch the annotation by hand.
@Slf4j
@RestController
@RequestMapping("/gest-service/detection-profile")
@RequiredArgsConstructor
public class DetectionProfileController {

    private final DetectionProfileUseCase detectionProfileUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('gest.detection-profile.publicar')")
    @AuditAction("UPDATE_DETECTION_PROFILE")
    public ResponseEntity<DetectionProfileResponse> publish(
            @RequestBody DetectionProfileRequest request) {
        DetectionProfileResponse response = detectionProfileUseCase.publish(request);
        if (response == null) {
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.ok(response);
    }

    @GetMapping("/active")
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<DetectionProfileResponse> findActive() {
        Optional<DetectionProfileResponse> result = detectionProfileUseCase.findActive();
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{version}")
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<DetectionProfileResponse> findByVersion(@PathVariable long version) {
        Optional<DetectionProfileResponse> result = detectionProfileUseCase.findByVersion(version);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/devices/{deviceId}")
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<DetectionProfileResponse> findForDevice(@PathVariable UUID deviceId) {
        Optional<DetectionProfileResponse> result = detectionProfileUseCase.findForDevice(deviceId);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }
}
