package com.neuroassist.edge.domain.service;

import com.neuroassist.edge.domain.model.DispositivoEdge;
import com.neuroassist.edge.domain.model.PairingState;
import java.time.Instant;

/**
 * Domain service for DispositivoEdge invariants and derived-value computation. Pure POJO — zero
 * Spring/JPA imports (RULE-01, RULE-02). Registered as @Bean in DomainServiceConfig (CLAUDE.md
 * rule).
 */
public class DispositivoEdgeDomainService {

    /**
     * Derive liveness status at the given instant. ADR-006: status is never stored, always computed
     * from {@code ultimaSincronizacao}.
     *
     * @param device the device aggregate
     * @param serverNow caller-supplied server clock (Instant.now() from service)
     * @param stalenessThresholdSecs the configured staleness window
     * @return "online", "offline", or "n/a" (for unpaired/retired devices)
     */
    public String deriveLivenessStatus(
            DispositivoEdge device, Instant serverNow, long stalenessThresholdSecs) {
        PairingState state = device.pairingState();
        if (state == PairingState.unpaired || state == PairingState.retired) {
            return "n/a";
        }
        return isOnlineAt(device, serverNow, stalenessThresholdSecs) ? "online" : "offline";
    }

    /**
     * Check if device was online at the given instant. Used for liveness-crossing detection during
     * heartbeat processing.
     */
    public boolean isOnlineAt(
            DispositivoEdge device, Instant serverNow, long stalenessThresholdSecs) {
        if (device.pairingState() == PairingState.unpaired
                || device.pairingState() == PairingState.retired) {
            return false;
        }
        Instant lastSync = device.ultimaSincronizacao();
        if (lastSync == null) {
            return false;
        }
        long ageSeconds = serverNow.getEpochSecond() - lastSync.getEpochSecond();
        return ageSeconds <= stalenessThresholdSecs;
    }

    /**
     * Whether a pair operation is valid for the current device state. Only registered and unpaired
     * devices may be paired.
     */
    public boolean canPair(DispositivoEdge device) {
        return device.pairingState() == PairingState.registered
                || device.pairingState() == PairingState.unpaired;
    }

    /**
     * Whether an unpair operation is valid for the current device state. Only paired devices may be
     * unpaired.
     */
    public boolean canUnpair(DispositivoEdge device) {
        return device.pairingState() == PairingState.paired;
    }

    /** Whether the device is in the terminal retired state. */
    public boolean isRetired(DispositivoEdge device) {
        return device.pairingState() == PairingState.retired;
    }

    /** Whether the device has completed enrollment (any state beyond registered). */
    public boolean isEnrolled(DispositivoEdge device) {
        return device.pairingState() != PairingState.registered;
    }
}
