package com.neuroassist.acs.infrastructure.clients;

import java.util.UUID;

/**
 * 200 response of {@code POST /internal/devices/verify-credential} on edge-service ({@code
 * standards/service-identity.md} §7.3). An invalid credential answers 401, not {@code valid:false},
 * but the flag is honoured as well so a stricter EDGE response shape cannot be read as success.
 *
 * <p>Declared {@code public} for the same reason as the request record: JDK dynamic proxies cannot
 * access package-private types (CLAUDE.md).
 *
 * @param valid whether the credential pair is currently valid
 * @param deviceId the device the credential belongs to
 * @param tenantId tenant the device belongs to — the sole trusted source of the token's tenant
 */
public record DeviceCredentialVerifyResponse(boolean valid, UUID deviceId, UUID tenantId) {}
