package com.neuroassist.edge.application.port.in;

import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port — liveness derivation and device/patient resolution (EDGE-009, P2). tenantId is
 * resolved internally via TenantContextHolder (JWT, never from body/path). status is always derived
 * at read time (ADR-006, data-model.md §1).
 */
public interface LivenessUseCase {

    /**
     * Evaluate liveness for a specific device. {@code GET /api/v1/edge/devices/{deviceId}/liveness}
     */
    LivenessResult evaluate(UUID deviceId);

    /**
     * Evaluate liveness for the patient's active paired device. {@code GET
     * /api/v1/edge/patients/{pacienteId}/liveness}
     */
    LivenessResult evaluateForPatient(UUID pacienteId);

    /**
     * Return the currently paired device for a patient. {@code GET
     * /api/v1/edge/patients/{pacienteId}/device}
     */
    DeviceResult resolveDeviceForPatient(UUID pacienteId);

    /**
     * Return the tenant reference for a device (GEST FROZEN endpoint §4.2). {@code GET
     * /api/v1/edge/devices/{deviceId}/tenant} Cross-tenant requests resolve as 404 (not 403) —
     * prevents enumeration (SPEC-06).
     */
    TenantResult resolveTenant(UUID deviceId);

    /**
     * Return full device data with derived fields for PAC/DASH/operator. {@code GET
     * /api/v1/edge/devices/{deviceId}}
     */
    DeviceResult getDevice(UUID deviceId);

    // ----------- Result records -----------

    record LivenessResult(
            UUID deviceId,
            UUID pacienteId,
            String status,
            Instant ultimaSincronizacao,
            String versao,
            long stalenessThresholdSeconds,
            Instant serverTime,
            String eTag) {}

    record DeviceResult(
            UUID deviceId,
            String tenantRef,
            UUID pacienteId,
            String pairingState,
            String status,
            Instant ultimaSincronizacao,
            String versao,
            Long runningProfileVersion,
            Long currentProfileVersion,
            Boolean profileDrift,
            long stalenessThresholdSeconds) {}

    record TenantResult(UUID deviceId, String tenantRef) {}
}
