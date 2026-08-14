package com.neuroassist.gest.api.controller;

import com.neuroassist.gest.api.dto.DetectionProfileRequest;
import com.neuroassist.gest.api.dto.DetectionProfileResponse;
import com.neuroassist.gest.application.port.in.DetectionProfileUseCase;
import com.neuroassist.gest.application.port.out.EdgePairingPort;
import com.neuroassist.gest.shared.audit.AuditAction;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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
    private final EdgePairingPort edgePairingPort;

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

    /**
     * Returns the active detection profile with conditional-GET support (ETag / 304).
     *
     * <p>If the client sends {@code If-None-Match} matching the current profile's ETag, responds
     * with {@code 304 Not Modified} (no body). Otherwise responds with {@code 200 OK}, setting
     * {@code ETag} and {@code Cache-Control: max-age=60} headers.
     */
    @GetMapping("/active")
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<DetectionProfileResponse> findActive(HttpServletRequest request) {
        Optional<DetectionProfileResponse> result = detectionProfileUseCase.findActive();
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DetectionProfileResponse profile = result.get();
        String clientEtag = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (profile.etag() != null && profile.etag().equals(clientEtag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, profile.etag())
                .header(HttpHeaders.CACHE_CONTROL, "max-age=60")
                .body(profile);
    }

    @GetMapping("/{version}")
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<DetectionProfileResponse> findByVersion(@PathVariable long version) {
        Optional<DetectionProfileResponse> result = detectionProfileUseCase.findByVersion(version);
        return result.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns the active detection profile for a given device, after verifying pairing with
     * edge-service. Supports conditional-GET (ETag / 304) identically to {@link
     * #findActive(HttpServletRequest)}.
     *
     * <p>If the device is not paired, {@link EdgePairingPort} throws a {@code
     * CodedException("device-not-paired")} mapped to 403 by {@link
     * com.neuroassist.gest.api.exception.GlobalExceptionHandler}.
     */
    @GetMapping("/devices/{deviceId}")
    @PreAuthorize("hasAuthority('gest.detection-profile.read')")
    public ResponseEntity<DetectionProfileResponse> findForDevice(
            @PathVariable UUID deviceId, HttpServletRequest request) {
        edgePairingPort.checkPairingAndGetTenant(deviceId);
        Optional<DetectionProfileResponse> result = detectionProfileUseCase.findForDevice(deviceId);
        if (result.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        DetectionProfileResponse profile = result.get();
        String clientEtag = request.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (profile.etag() != null && profile.etag().equals(clientEtag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, profile.etag())
                .header(HttpHeaders.CACHE_CONTROL, "max-age=60")
                .body(profile);
    }
}
