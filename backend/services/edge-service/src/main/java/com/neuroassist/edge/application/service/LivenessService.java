package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.LivenessUseCase;
import com.neuroassist.edge.application.port.out.DetectionProfilePort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.domain.event.DispositivoFicouOffline;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * LivenessService — EDGE-009 (P2). Derives liveness at read time — status is never a stored column
 * (ADR-006). tenantId obtained via TenantContextHolder.require() — never from caller parameters.
 * ETag computation for bandwidth-friendly DASH polling (api-contract §4.4). RULE-03: @Transactional
 * only in application.service (read-only for query operations).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LivenessService implements LivenessUseCase {

    private final DispositivoEdgeRepositoryPort deviceRepo;
    private final DetectionProfilePort detectionProfilePort;
    private final DispositivoEdgeDomainService domainService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${neuroassist.edge.heartbeat.interval-seconds:30}")
    private long heartbeatIntervalSeconds;

    @Value("${neuroassist.edge.heartbeat.staleness-factor:3}")
    private long stalenessFactor;

    @Override
    public LivenessResult evaluate(UUID deviceId) {
        String tenantId = TenantContextHolder.require();
        DispositivoEdge device =
                deviceRepo
                        .findByIdAndTenantId(deviceId, tenantId)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.device-not-found",
                                                "Device not found: " + deviceId));
        return buildLivenessResult(device);
    }

    @Override
    public LivenessResult evaluateForPatient(UUID pacienteId) {
        String tenantId = TenantContextHolder.require();
        DispositivoEdge device =
                deviceRepo
                        .findByPairingStateAndPacienteIdAndTenantId(
                                PairingState.paired, pacienteId, tenantId)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.patient-not-paired",
                                                "No active paired device for patient: "
                                                        + pacienteId));
        return buildLivenessResult(device);
    }

    @Override
    public DeviceResult resolveDeviceForPatient(UUID pacienteId) {
        String tenantId = TenantContextHolder.require();
        DispositivoEdge device =
                deviceRepo
                        .findByPairingStateAndPacienteIdAndTenantId(
                                PairingState.paired, pacienteId, tenantId)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.patient-not-paired",
                                                "No active paired device for patient: "
                                                        + pacienteId));
        return buildDeviceResult(device, tenantId);
    }

    @Override
    public TenantResult resolveTenant(UUID deviceId) {
        String tenantId = TenantContextHolder.require();
        // GEST FROZEN endpoint: return only deviceId + tenantRef
        // Retired devices respond 404 — prevents tenant enumeration (SPEC-06)
        DispositivoEdge device =
                deviceRepo
                        .findByIdAndTenantId(deviceId, tenantId)
                        .filter(d -> d.pairingState() != PairingState.retired)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.device-not-found",
                                                "Device not found: " + deviceId));
        return new TenantResult(device.id(), device.tenantId());
    }

    @Override
    public DeviceResult getDevice(UUID deviceId) {
        String tenantId = TenantContextHolder.require();
        DispositivoEdge device =
                deviceRepo
                        .findByIdAndTenantId(deviceId, tenantId)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.device-not-found",
                                                "Device not found: " + deviceId));
        return buildDeviceResult(device, tenantId);
    }

    // ---- Private helpers ----

    private long stalenessThresholdSeconds() {
        return heartbeatIntervalSeconds * stalenessFactor;
    }

    private LivenessResult buildLivenessResult(DispositivoEdge device) {
        Instant serverNow = Instant.now();
        long threshold = stalenessThresholdSeconds();
        String status = domainService.deriveLivenessStatus(device, serverNow, threshold);

        // EDGE-016 (P6): offline derived at read-time — emit event so listeners can log/metric.
        // The listener MUST NOT write state (ADR-006). Fires on every liveness poll revealing
        // offline; listener is idempotent (log only).
        if ("offline".equals(status)) {
            eventPublisher.publishEvent(
                    DispositivoFicouOffline.of(
                            device.id(), device.tenantId(), device.ultimaSincronizacao()));
        }

        // ETag incorporates the derived status bucket boundary (api-contract §4.4):
        // online→offline crossing always invalidates ETag even without a new heartbeat.
        String eTagInput =
                device.id()
                        + ":"
                        + status
                        + ":"
                        + (device.ultimaSincronizacao() != null
                                ? device.ultimaSincronizacao().getEpochSecond() / threshold
                                : "null");
        String eTag = "\"" + Integer.toHexString(eTagInput.hashCode()) + "\"";

        return new LivenessResult(
                device.id(),
                device.pacienteId(),
                status,
                device.ultimaSincronizacao(),
                device.versao(),
                threshold,
                serverNow,
                eTag);
    }

    private DeviceResult buildDeviceResult(DispositivoEdge device, String tenantId) {
        Instant serverNow = Instant.now();
        long threshold = stalenessThresholdSeconds();
        String status = domainService.deriveLivenessStatus(device, serverNow, threshold);

        // Detection profile drift (stub returns empty in P2; GEST in P4)
        Optional<Long> currentProfileOpt = Optional.empty();
        try {
            currentProfileOpt = detectionProfilePort.currentProfileVersion(device.id(), tenantId);
        } catch (Exception e) {
            log.warn("GEST unavailable resolving device profile deviceId='{}'", device.id());
        }

        Long currentProfileVersion = currentProfileOpt.orElse(null);
        Boolean profileDrift =
                (currentProfileVersion != null && device.runningProfileVersion() != null)
                        ? !currentProfileVersion.equals(device.runningProfileVersion())
                        : null;

        return new DeviceResult(
                device.id(),
                device.tenantId(),
                device.pacienteId(),
                device.pairingState().name(),
                status,
                device.ultimaSincronizacao(),
                device.versao(),
                device.runningProfileVersion(),
                currentProfileVersion,
                profileDrift,
                threshold);
    }
}
