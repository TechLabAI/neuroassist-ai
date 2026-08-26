package com.neuroassist.acs.infrastructure.clients;

/**
 * Body of {@code POST /internal/devices/verify-credential} on edge-service ({@code
 * standards/service-identity.md} §7.3).
 *
 * <p>Declared {@code public}: JDK dynamic proxies ({@code jdk.proxy*.$ProxyN}) generated for Feign
 * interfaces cannot reach package-private types.
 *
 * <p>The secret leaves acs-service only on this hop, over the internal ACS↔EDGE call, and is never
 * logged (Feign logging is {@code BASIC}, which omits bodies).
 */
public record DeviceCredentialVerifyRequest(String clientId, String clientSecret) {

    /** Keeps the secret out of any accidental string interpolation. */
    @Override
    public String toString() {
        return "DeviceCredentialVerifyRequest[clientId=" + clientId + "]";
    }
}
