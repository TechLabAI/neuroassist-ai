package com.neuroassist.acs.application.port.out;

import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port — asks edge-service whether a device credential pair is valid (E2E-005, {@code
 * standards/service-identity.md} §7).
 *
 * <p>acs-service deliberately does not store or check device secrets: edge-service issued the
 * credential at enroll, keeps its SHA-256 hash inside the tenant schema and knows whether the device
 * was retired. Ownership is split — EDGE owns the credential, ACS owns token issuance — so the
 * device-token flow crosses the boundary exactly once.
 *
 * <p>The port is intentionally coarse: it answers "valid, and if so which device of which tenant",
 * and nothing else. Any failure (invalid pair, edge-service down, malformed answer) collapses into
 * an empty {@link Optional}, because the caller must return the same generic 401 for all of them.
 */
public interface DeviceCredentialVerificationPort {

    /**
     * Verifies a device credential pair.
     *
     * @param clientId device client id, e.g. {@code edge-<deviceId>}
     * @param clientSecret the device secret; never logged, never persisted by acs-service
     * @return the verified device identity, or empty when the credential is rejected or the
     *     verification could not be performed
     */
    Optional<VerifiedDevice> verify(String clientId, String clientSecret);

    /**
     * Identity of a device whose credential edge-service confirmed.
     *
     * @param deviceId the device's own id (informational — carried in logs/metrics)
     * @param tenantId tenant the device belongs to; the only trusted source of the token's tenant
     *     claims, since the device is unauthenticated when it asks for a token
     */
    record VerifiedDevice(UUID deviceId, UUID tenantId) {}
}
