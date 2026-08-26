package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.api.dto.HeartbeatRequest;
import com.neuroassist.edge.api.dto.HeartbeatResponse;
import com.neuroassist.edge.application.port.in.HeartbeatUseCase;
import com.neuroassist.edge.shared.error.CodedException;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Heartbeat ingestion endpoint (api-contract §3.1). POST /api/v1/edge/heartbeat — requires device
 * credential (edge.device scope). deviceId extracted from the authenticated device token — never
 * from body (ADR-005 #2). tenantId resolved by application service from TenantContextHolder (device
 * JWT). RULE-04: no imports from infrastructure.* in this class.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/edge/heartbeat")
@RequiredArgsConstructor
public class HeartbeatController {

    private final HeartbeatUseCase heartbeatUseCase;

    @PostMapping
    @PreAuthorize("hasAuthority('edge.device')")
    public ResponseEntity<HeartbeatResponse> recordHeartbeat(
            @Valid @RequestBody HeartbeatRequest req, Authentication auth) {
        // deviceId from device JWT sub claim — never from body (anti-spoofing invariant #2,
        // ADR-005)
        UUID deviceId = extractDeviceId(auth);
        // Anti-spoofing: if the body declares a deviceId, it must match the JWT device.
        // A body claiming a different device is rejected with 403 (edge.identity-mismatch).
        if (req.deviceId() != null && !req.deviceId().equals(deviceId)) {
            throw new CodedException(
                    "edge.identity-mismatch",
                    "Body deviceId "
                            + req.deviceId()
                            + " does not match authenticated device "
                            + deviceId);
        }
        HeartbeatUseCase.HeartbeatResult result =
                heartbeatUseCase.recordHeartbeat(
                        deviceId, req.agentVersion(), req.runningProfileVersion());
        HeartbeatResponse body =
                new HeartbeatResponse(
                        result.deviceId(),
                        result.status(),
                        result.serverTime(),
                        result.ultimaSincronizacao(),
                        result.currentProfileVersion(),
                        result.profileDrift(),
                        result.profilePointerDegraded(),
                        result.stalenessThresholdSeconds(),
                        result.nextHeartbeatSeconds());
        return ResponseEntity.ok(body);
    }

    /**
     * Extract deviceId from the authenticated device token. Device JWT sub claim = deviceId
     * (api-contract §6.3). P5 wires the full JWT decoder; for P2 reads auth.getName() which maps to
     * 'sub'.
     */
    private static UUID extractDeviceId(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("No authenticated principal — device token required");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            // Fallback: clientId may be "edge-<uuid>" in some token configurations
            String name = auth.getName();
            if (name.startsWith("edge-")) {
                return UUID.fromString(name.substring(5));
            }
            throw new IllegalStateException("Cannot derive deviceId from principal: " + name, e);
        }
    }
}
