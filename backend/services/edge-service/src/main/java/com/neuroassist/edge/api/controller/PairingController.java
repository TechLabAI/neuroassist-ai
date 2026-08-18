package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.api.dto.DeviceResponse;
import com.neuroassist.edge.api.dto.PairingRequest;
import com.neuroassist.edge.api.dto.PairingResponse;
import com.neuroassist.edge.api.dto.UnpairRequest;
import com.neuroassist.edge.application.port.in.LivenessUseCase;
import com.neuroassist.edge.application.port.in.PairingUseCase;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Exclusive device-patient pairing and patient-device resolution endpoints. RULE-04: no imports
 * from infrastructure.* in this class. tenantId resolved by application service from
 * TenantContextHolder.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class PairingController {

    private final PairingUseCase pairingUseCase;
    private final LivenessUseCase livenessUseCase;

    /** POST /api/v1/edge/devices/{id}/pairings — pair device to patient (operator). */
    @PostMapping("/api/v1/edge/devices/{id}/pairings")
    @PreAuthorize("hasAuthority('edge.manage')")
    public ResponseEntity<PairingResponse> pair(
            @PathVariable UUID id, @Valid @RequestBody PairingRequest req, Authentication auth) {
        String actor = auth != null ? auth.getName() : "unknown";
        DispositivoEdge device = pairingUseCase.pair(id, req.pacienteId(), actor);
        PairingResponse body =
                new PairingResponse(
                        device.id(),
                        device.tenantId(),
                        device.pacienteId(),
                        device.pairingState().name(),
                        device.createdAt(),
                        device.updatedAt());
        return ResponseEntity.created(URI.create("/api/v1/edge/devices/" + id + "/pairings"))
                .body(body);
    }

    /** DELETE /api/v1/edge/devices/{id}/pairings/current — unpair device (operator). */
    @DeleteMapping("/api/v1/edge/devices/{id}/pairings/current")
    @PreAuthorize("hasAuthority('edge.manage')")
    public ResponseEntity<Void> unpair(
            @PathVariable UUID id,
            @RequestBody(required = false) UnpairRequest req,
            Authentication auth) {
        String actor = auth != null ? auth.getName() : "unknown";
        String reason = req != null ? req.reason() : null;
        pairingUseCase.unpair(id, reason, actor);
        return ResponseEntity.noContent().build();
    }

    /**
     * GET /api/v1/edge/patients/{pacienteId}/device — resolve paired device for patient (PAC). 404
     * if no paired device in caller's tenant.
     */
    @GetMapping("/api/v1/edge/patients/{pacienteId}/device")
    @PreAuthorize("hasAnyAuthority('edge.read', 'edge.internal')")
    public ResponseEntity<DeviceResponse> resolveDeviceForPatient(@PathVariable UUID pacienteId) {
        LivenessUseCase.DeviceResult r = livenessUseCase.resolveDeviceForPatient(pacienteId);
        return ResponseEntity.ok(toDeviceResponse(r));
    }

    private static DeviceResponse toDeviceResponse(LivenessUseCase.DeviceResult r) {
        return new DeviceResponse(
                r.deviceId(),
                r.tenantRef(),
                r.pacienteId(),
                r.pairingState(),
                r.status(),
                r.ultimaSincronizacao(),
                r.versao(),
                r.runningProfileVersion(),
                r.currentProfileVersion(),
                r.profileDrift(),
                r.stalenessThresholdSeconds());
    }
}
