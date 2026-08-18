package com.neuroassist.edge.application.port.in;

import com.neuroassist.edge.domain.model.DispositivoEdge;
import java.util.UUID;

/**
 * Inbound port — exclusive device-patient pairing. tenantId is resolved internally via
 * TenantContextHolder (set by TenantContextFilter from JWT). EDGE-007 (P2): pair + unpair golden
 * path.
 */
public interface PairingUseCase {

    /**
     * Pair a device to a patient (operator action).
     *
     * @param deviceId the device UUID
     * @param pacienteId the patient cross-reference (not validated against PAC at pair time)
     * @param actor operator identity (from JWT sub claim)
     */
    DispositivoEdge pair(UUID deviceId, UUID pacienteId, String actor);

    /**
     * Unpair the current device-patient association (operator action).
     *
     * @param deviceId the device UUID
     * @param reason human-readable reason for the audit trail
     * @param actor operator identity (from JWT sub claim)
     */
    void unpair(UUID deviceId, String reason, String actor);
}
