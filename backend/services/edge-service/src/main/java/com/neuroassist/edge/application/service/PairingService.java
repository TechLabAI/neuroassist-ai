package com.neuroassist.edge.application.service;

import com.neuroassist.edge.application.port.in.PairingUseCase;
import com.neuroassist.edge.application.port.out.DispositivoEdgeRepositoryPort;
import com.neuroassist.edge.application.port.out.DispositivoEdgeTransitionRepositoryPort;
import com.neuroassist.edge.domain.event.DispositivoDespareado;
import com.neuroassist.edge.domain.event.DispositivoPareado;
import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.DispositivoEdgeTransition;
import com.neuroassist.edge.domain.model.PairingState;
import com.neuroassist.edge.domain.service.DispositivoEdgeDomainService;
import com.neuroassist.edge.infrastructure.security.TenantContextHolder;
import com.neuroassist.edge.shared.error.CodedException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * PairingService — EDGE-007 (P2): pair + unpair golden path. tenantId obtained via
 * TenantContextHolder.require() — never from caller parameters. RULE-03: @Transactional only in
 * application.service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PairingService implements PairingUseCase {

    private final DispositivoEdgeRepositoryPort deviceRepo;
    private final DispositivoEdgeTransitionRepositoryPort transitionRepo;
    private final DispositivoEdgeDomainService domainService;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public DispositivoEdge pair(UUID deviceId, UUID pacienteId, String actor) {
        String tenantId = TenantContextHolder.require();
        log.info(
                "Pairing deviceId='{}' pacienteId='{}' tenant='{}' actor='{}'",
                deviceId,
                pacienteId,
                tenantId,
                actor);

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
                    "Device " + deviceId + " is retired and cannot be paired.");
        }

        if (!domainService.canPair(device)) {
            throw new CodedException(
                    "edge.pairing-conflict", "Device " + deviceId + " is already paired.");
        }

        // Exclusive-pairing invariant: no other paired device for this patient
        deviceRepo
                .findByPairingStateAndPacienteIdAndTenantId(
                        PairingState.paired, pacienteId, tenantId)
                .ifPresent(
                        existing -> {
                            throw new CodedException(
                                    "edge.pairing-conflict",
                                    "Patient "
                                            + pacienteId
                                            + " already has an active paired device: "
                                            + existing.id());
                        });

        String fromState = device.pairingState().name();
        DispositivoEdge updated = device.withPaired(pacienteId, actor);
        updated = deviceRepo.save(updated);

        // Append audited pairing transition (ADR-005 invariant #3)
        DispositivoEdgeTransition transition =
                DispositivoEdgeTransition.pairing(
                        deviceId, tenantId, fromState, PairingState.paired.name(), actor, null);
        transitionRepo.save(transition);

        log.info("Device paired deviceId='{}' pacienteId='{}'", deviceId, pacienteId);

        // EDGE-016 (P6): publish domain event pos-commit
        eventPublisher.publishEvent(DispositivoPareado.of(deviceId, tenantId, pacienteId, actor));

        return updated;
    }

    @Override
    public void unpair(UUID deviceId, String reason, String actor) {
        String tenantId = TenantContextHolder.require();
        log.info("Unpairing deviceId='{}' tenant='{}' actor='{}'", deviceId, tenantId, actor);

        DispositivoEdge device =
                deviceRepo
                        .findByIdAndTenantId(deviceId, tenantId)
                        .orElseThrow(
                                () ->
                                        new CodedException(
                                                "edge.device-not-found",
                                                "Device not found: " + deviceId));

        if (!domainService.canUnpair(device)) {
            throw new CodedException(
                    "edge.illegal-pairing-transition",
                    "Device "
                            + deviceId
                            + " is not currently paired (state: "
                            + device.pairingState()
                            + ").");
        }

        String fromState = device.pairingState().name();
        DispositivoEdge updated = device.withUnpaired(actor);
        deviceRepo.save(updated);

        // Append audited pairing transition
        DispositivoEdgeTransition transition =
                DispositivoEdgeTransition.pairing(
                        deviceId, tenantId, fromState, PairingState.unpaired.name(), actor, reason);
        transitionRepo.save(transition);

        log.info("Device unpaired deviceId='{}'", deviceId);

        // EDGE-016 (P6): publish domain event pos-commit
        eventPublisher.publishEvent(DispositivoDespareado.of(deviceId, tenantId, reason, actor));
    }
}
