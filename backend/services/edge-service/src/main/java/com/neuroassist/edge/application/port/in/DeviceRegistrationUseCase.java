package com.neuroassist.edge.application.port.in;

import com.neuroassist.edge.domain.model.PairingState;
import java.time.Instant;
import java.util.UUID;

/**
 * Inbound port — device registration lifecycle. tenantId is resolved internally via
 * TenantContextHolder (set by TenantContextFilter from JWT). EDGE-005: register + enrollment token
 * (P2). EDGE-006: enroll → credential (P2). EDGE-015: retire (P4 — stub only here).
 */
public interface DeviceRegistrationUseCase {

    /**
     * Register a new device (operator action). Mints a one-time enrollment token (TTL = configured
     * seconds). tenantId derived from TenantContextHolder (JWT), never from parameters.
     *
     * @param label human-readable device label
     * @param pacienteHint advisory patient reference (does not trigger pairing)
     * @param actor operator's identity (from JWT sub claim)
     */
    RegisterResult register(String label, String pacienteHint, String actor);

    /**
     * Exchange one-time enrollment token for a device credential (edge agent action). No JWT
     * required — guarded by the single-use token. clientSecret returned exactly once; only its hash
     * is persisted.
     *
     * @param enrollmentToken plaintext token presented by the agent
     * @param agentVersion initial agent version string
     */
    EnrollResult enroll(String enrollmentToken, String agentVersion);

    /**
     * Retire a device (operator action). Terminal state — revokes credential, drops active pairing
     * (audited). P4 provides the full implementation.
     *
     * @param deviceId the device to retire
     * @param actor operator identity
     */
    void retire(UUID deviceId, String actor);

    // ----------- Result records (nested for port cohesion) -----------

    record RegisterResult(
            UUID deviceId,
            PairingState pairingState,
            String label,
            String enrollmentToken,
            Instant enrollmentExpiresAt,
            Instant createdAt) {}

    record EnrollResult(
            UUID deviceId,
            String tenantRef,
            String clientId,
            String clientSecret,
            String tokenEndpoint,
            String tokenScope,
            int recommendedHeartbeatSeconds) {}
}
