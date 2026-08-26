package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.api.dto.DeviceListResponse;
import com.neuroassist.edge.api.dto.DeviceRegistrationRequest;
import com.neuroassist.edge.api.dto.DeviceRegistrationResponse;
import com.neuroassist.edge.api.dto.DeviceResponse;
import com.neuroassist.edge.api.dto.EnrollRequest;
import com.neuroassist.edge.api.dto.EnrollResponse;
import com.neuroassist.edge.application.port.in.DeviceListUseCase;
import com.neuroassist.edge.application.port.in.DeviceRegistrationUseCase;
import com.neuroassist.edge.application.port.in.LivenessUseCase;
import com.neuroassist.edge.application.service.DeviceEnrollmentService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device registration and enrollment endpoints (api-contract §2, §4.1, §4.2). Base path:
 * /api/v1/edge/devices. POST /enroll: no Bearer JWT required — guarded by single-use enrollment
 * token. tenantId: resolved by application service from TenantContextHolder (set by
 * TenantContextFilter from JWT) — never from body or path (api-contract §1). deviceId: from
 * authenticated principal for heartbeat — never from body (ADR-005 invariant #2). RULE-04: no
 * imports from infrastructure.* in this class.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/edge/devices")
@RequiredArgsConstructor
public class DeviceRegistrationController {

    private final DeviceRegistrationUseCase deviceRegistrationUseCase;
    private final DeviceEnrollmentService deviceEnrollmentService;
    private final LivenessUseCase livenessUseCase;
    private final DeviceListUseCase deviceListUseCase;

    /**
     * GET /api/v1/edge/devices — paged list of all devices for the current tenant (EDGE P9).
     * tenantId resolved from JWT via TenantContextHolder — never from request parameters.
     */
    @GetMapping
    @PreAuthorize("hasAnyAuthority('edge.manage', 'edge.read', 'edge.internal')")
    public ResponseEntity<Page<DeviceListResponse>> listDevices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        Pageable pageable = PageRequest.of(page, Math.min(size, 100));
        Page<DeviceListResponse> result =
                deviceListUseCase
                        .listDevices(pageable)
                        .map(
                                r ->
                                        new DeviceListResponse(
                                                r.deviceId(),
                                                r.label(),
                                                r.pairingState(),
                                                r.livenessStatus(),
                                                r.lastSeen(),
                                                r.registeredAt()));
        return ResponseEntity.ok(result);
    }

    /** POST /api/v1/edge/devices — register a new device (operator, edge.manage). */
    @PostMapping
    @PreAuthorize("hasAuthority('edge.manage')")
    public ResponseEntity<DeviceRegistrationResponse> register(
            @Valid @RequestBody DeviceRegistrationRequest req, Authentication auth) {
        String actor = auth != null ? auth.getName() : "unknown";
        DeviceRegistrationUseCase.RegisterResult result =
                deviceRegistrationUseCase.register(req.label(), req.pacienteHint(), actor);
        DeviceRegistrationResponse body =
                new DeviceRegistrationResponse(
                        result.deviceId(),
                        result.pairingState().name(),
                        result.label(),
                        result.enrollmentToken(),
                        result.enrollmentExpiresAt(),
                        result.createdAt());
        return ResponseEntity.created(URI.create("/api/v1/edge/devices/" + result.deviceId()))
                .body(body);
    }

    /**
     * POST /api/v1/edge/devices/enroll — exchange enrollment token for device credential. No Bearer
     * JWT required — configured as permitAll in SecurityConfig.
     */
    @PostMapping("/enroll")
    public ResponseEntity<EnrollResponse> enroll(@Valid @RequestBody EnrollRequest req) {
        // Via DeviceEnrollmentService: resolve o tenant pelo índice global ANTES da transação
        // (o enroll é anônimo, então não há claim de tenant — C-21).
        DeviceRegistrationUseCase.EnrollResult result =
                deviceEnrollmentService.enroll(req.enrollmentToken(), req.agentVersion());
        EnrollResponse body =
                new EnrollResponse(
                        result.deviceId(),
                        result.tenantRef(),
                        result.clientId(),
                        result.clientSecret(),
                        result.tokenEndpoint(),
                        result.tokenScope(),
                        result.recommendedHeartbeatSeconds());
        return ResponseEntity.ok(body);
    }

    /** POST /api/v1/edge/devices/{id}:retire — retire a device (P4 stub). */
    @PostMapping("/{id}:retire")
    @PreAuthorize("hasAuthority('edge.manage')")
    public ResponseEntity<Void> retire(@PathVariable UUID id, Authentication auth) {
        String actor = auth != null ? auth.getName() : "unknown";
        deviceRegistrationUseCase.retire(id, actor);
        return ResponseEntity.ok().build();
    }

    /** GET /api/v1/edge/devices/{deviceId} — full device with derived status/drift. */
    @GetMapping("/{deviceId}")
    @PreAuthorize("hasAnyAuthority('edge.read', 'edge.internal')")
    public ResponseEntity<DeviceResponse> getDevice(@PathVariable UUID deviceId) {
        LivenessUseCase.DeviceResult r = livenessUseCase.getDevice(deviceId);
        return ResponseEntity.ok(toDeviceResponse(r));
    }

    /**
     * GET /api/v1/edge/devices/{deviceId}/tenant — device→tenant resolution (GEST FROZEN §4.2).
     * Returns only deviceId + tenantRef — no patient, liveness, or credential data.
     */
    @GetMapping("/{deviceId}/tenant")
    @PreAuthorize("hasAuthority('edge.internal')")
    public ResponseEntity<?> resolveTenant(@PathVariable UUID deviceId) {
        LivenessUseCase.TenantResult r = livenessUseCase.resolveTenant(deviceId);
        return ResponseEntity.ok(new TenantRefView(r.deviceId(), r.tenantRef()));
    }

    // ---- Private mappers ----

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

    /** Inline record for the tenant-only response (api-contract §4.2). */
    public record TenantRefView(UUID deviceId, String tenantRef) {}
}
