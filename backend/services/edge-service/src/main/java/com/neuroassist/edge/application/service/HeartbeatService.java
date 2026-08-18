package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.HeartbeatUseCase;
import com.neuroassist.edge.application.port.out.DetectionProfilePort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeTransitionRepositoryPort;
import com.neuroassist.edge.domain.event.DispositivoVoltouOnline;
import com.neuroassist.edge.domain.event.HeartbeatRecebido;
import com.neuroassist.edge.domain.event.ProfileDriftDetected;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
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
 * HeartbeatService — EDGE-008 (P2). tenantId obtained via TenantContextHolder.require() — set by
 * TenantContextFilter from device JWT. ultimaSincronizacao stamped by server clock — never the
 * client clock (doc 06 R5). deviceId comes from the caller — extracted from SecurityContext by
 * controller, never from body. RULE-03: @Transactional only in application.service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class HeartbeatService implements HeartbeatUseCase {

    private final DispositivoEdgeRepositoryPort deviceRepo;
    private final DispositivoEdgeTransitionRepositoryPort transitionRepo;
    private final DetectionProfilePort detectionProfilePort;
    private final DispositivoEdgeDomainService domainService;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${neuroassist.edge.heartbeat.interval-seconds:30}")
    private long heartbeatIntervalSeconds;

    @Value("${neuroassist.edge.heartbeat.staleness-factor:3}")
    private long stalenessFactor;

    @Override
    public HeartbeatResult recordHeartbeat(
            UUID deviceId, String agentVersion, Long runningProfileVersion) {
        String tenantId = TenantContextHolder.require();
        log.debug(
                "Heartbeat received deviceId='{}' agentVersion='{}' tenant='{}'",
                deviceId,
                agentVersion,
                tenantId);

        DispositivoEdge device =
                deviceRepo
                        .findByIdAndTenantId(deviceId, tenantId)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.device-not-found",
                                                "Device not found: " + deviceId));

        if (domainService.isRetired(device)) {
            throw new CodedException(
                    "edge.device-retired",
                    "Device " + deviceId + " is retired — heartbeat not accepted.");
        }

        long stalenessThresholdSeconds = heartbeatIntervalSeconds * stalenessFactor;
        Instant serverNow = Instant.now();

        // Detect liveness crossing (offline → online) before updating
        boolean wasOnlineBefore =
                domainService.isOnlineAt(device, serverNow, stalenessThresholdSeconds);

        // Update device with server-stamped time (ADR-006 — never client clock)
        DispositivoEdge updated =
                device.withHeartbeat(agentVersion, runningProfileVersion, serverNow);
        updated = deviceRepo.save(updated);

        // If device was offline → append liveness transition (offline→online)
        if (!wasOnlineBefore) {
            String fromLiveness = (device.ultimaSincronizacao() == null) ? "offline" : "offline";
            DispositivoEdgeTransition livenessTransition =
                    DispositivoEdgeTransition.liveness(deviceId, tenantId, fromLiveness, "online");
            transitionRepo.save(livenessTransition);
            log.info("Liveness transition offline→online for deviceId='{}'", deviceId);
            // EDGE-016 (P6): liveness crossing event
            eventPublisher.publishEvent(DispositivoVoltouOnline.of(deviceId, tenantId));
        }

        // Derive current liveness after update (always online immediately after heartbeat for
        // paired)
        String livenessStatus =
                domainService.deriveLivenessStatus(updated, serverNow, stalenessThresholdSeconds);

        // Fetch detection profile version from GEST (stub returns empty in P2)
        Optional<Long> currentProfileOpt = Optional.empty();
        boolean profilePointerDegraded = false;
        try {
            currentProfileOpt = detectionProfilePort.currentProfileVersion(deviceId, tenantId);
        } catch (Exception e) {
            log.warn(
                    "GEST unavailable — heartbeat still accepted for deviceId='{}': {}",
                    deviceId,
                    e.getMessage());
            profilePointerDegraded = true;
        }
        if (currentProfileOpt.isEmpty() && !profilePointerDegraded) {
            // Stub returns empty (P2) — treat as degraded for honest response
            profilePointerDegraded = true;
        }

        Long currentProfileVersion = currentProfileOpt.orElse(null);
        Boolean profileDrift =
                (currentProfileVersion != null && runningProfileVersion != null)
                        ? !currentProfileVersion.equals(runningProfileVersion)
                        : null;

        log.debug(
                "Heartbeat processed deviceId='{}' status='{}' profileDegraded={}",
                deviceId,
                livenessStatus,
                profilePointerDegraded);

        // EDGE-016 (P6): always publish HeartbeatRecebido pos-commit
        eventPublisher.publishEvent(
                HeartbeatRecebido.of(
                        deviceId, tenantId, serverNow, agentVersion, runningProfileVersion));

        // EDGE-016 (P6): emit ProfileDriftDetected only when drift is present (device version !=
        // GEST version). The plan instructs: "emit only when drift changes". For MVP we emit
        // whenever
        // drift == true and both versions are known — the listener is idempotent (log only).
        if (Boolean.TRUE.equals(profileDrift)) {
            eventPublisher.publishEvent(
                    ProfileDriftDetected.of(
                            deviceId, tenantId, runningProfileVersion, currentProfileVersion));
        }

        return new HeartbeatResult(
                updated.id(),
                livenessStatus,
                serverNow,
                updated.ultimaSincronizacao(),
                currentProfileVersion,
                profileDrift,
                profilePointerDegraded,
                stalenessThresholdSeconds,
                heartbeatIntervalSeconds);
    }
}
