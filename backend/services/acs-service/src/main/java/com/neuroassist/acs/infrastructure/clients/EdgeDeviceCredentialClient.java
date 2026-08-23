package com.neuroassist.acs.infrastructure.clients;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for device credential verification on edge-service (E2E-005, {@code
 * standards/service-identity.md} §7.3). RULE-06: {@code @FeignClient} only in
 * infrastructure.clients.
 *
 * <p>{@code /internal/**} has no route in the gateway (ADR-008), so this call is reachable only
 * inside the cluster, and edge-service still requires the {@code edge.internal} authority on it —
 * supplied by {@link InternalServiceIdentityInterceptor} through {@link EdgeInternalFeignConfig},
 * not by propagating the caller's token (there is no caller token: the device endpoint is public).
 */
@FeignClient(
        name = "edge-service",
        contextId = "edgeDeviceCredentialClient",
        url = "${neuroassist.edge.url:}",
        configuration = EdgeInternalFeignConfig.class)
public interface EdgeDeviceCredentialClient {

    /**
     * Verifies a device credential pair.
     *
     * @param request clientId + clientSecret
     * @return verification result including the device's tenant
     */
    @PostMapping("/internal/devices/verify-credential")
    DeviceCredentialVerifyResponse verifyCredential(
            @RequestBody DeviceCredentialVerifyRequest request);
}
