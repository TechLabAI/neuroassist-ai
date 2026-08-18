package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.api.dto.LivenessResponse;
import com.neuroassist.edge.application.port.in.LivenessUseCase;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness poll endpoints for DASH (api-contract §4.4). status derived at read time — never a
 * stored column (ADR-006). ETag/304 support for bandwidth-efficient DASH polling. serverTime
 * mandatory in every response (EDGE-004 / M3 warning closure). RULE-04: no imports from
 * infrastructure.* in this class. tenantId resolved by application service from
 * TenantContextHolder.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class LivenessController {

    private final LivenessUseCase livenessUseCase;

    /** GET /api/v1/edge/devices/{deviceId}/liveness — liveness poll (DASH). */
    @GetMapping("/api/v1/edge/devices/{deviceId}/liveness")
    @PreAuthorize("hasAuthority('edge.read')")
    public ResponseEntity<LivenessResponse> getLivenessForDevice(
            @PathVariable UUID deviceId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        LivenessUseCase.LivenessResult result = livenessUseCase.evaluate(deviceId);
        return buildLivenessResponse(result, ifNoneMatch);
    }

    /** GET /api/v1/edge/patients/{pacienteId}/liveness — liveness poll by patient (DASH). */
    @GetMapping("/api/v1/edge/patients/{pacienteId}/liveness")
    @PreAuthorize("hasAuthority('edge.read')")
    public ResponseEntity<LivenessResponse> getLivenessForPatient(
            @PathVariable UUID pacienteId,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                    String ifNoneMatch) {
        LivenessUseCase.LivenessResult result = livenessUseCase.evaluateForPatient(pacienteId);
        return buildLivenessResponse(result, ifNoneMatch);
    }

    // ---- Private helpers ----

    private ResponseEntity<LivenessResponse> buildLivenessResponse(
            LivenessUseCase.LivenessResult result, String ifNoneMatch) {
        String eTag = result.eTag();

        // 304: status unchanged within the same bucket boundary (api-contract §4.4)
        if (ifNoneMatch != null && ifNoneMatch.equals(eTag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .header(HttpHeaders.ETAG, eTag)
                    .build();
        }

        LivenessResponse body =
                new LivenessResponse(
                        result.deviceId(),
                        result.pacienteId(),
                        result.status(),
                        result.ultimaSincronizacao(),
                        result.versao(),
                        result.stalenessThresholdSeconds(),
                        result.serverTime());

        return ResponseEntity.ok()
                .header(HttpHeaders.ETAG, eTag)
                .header(
                        "X-Staleness-Threshold-Seconds",
                        String.valueOf(result.stalenessThresholdSeconds()))
                .body(body);
    }
}
