package com.neuroassist.gest.infrastructure.clients;

import com.neuroassist.gest.infrastructure.clients.dto.TenantResponse;
import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for the edge-service pairing endpoint (RULE-06: @FeignClient only in
 * infrastructure.clients). Configured via {@code gest.edge.url} property.
 *
 * <p>In the {@code poc} profile the {@code EdgePairingPortStub} overrides this client's adapter via
 * {@code @Primary}.
 */
@FeignClient(name = "edge-service", url = "${gest.edge.url:}")
public interface EdgePairingClient {

    /**
     * Returns the tenant associated with a device.
     *
     * @param deviceId the device UUID
     * @return {@link TenantResponse} with tenantId and paired flag
     * @throws feign.FeignException.NotFound if the device does not exist in edge-service
     */
    @GetMapping("/api/v1/edge/devices/{deviceId}/tenant")
    TenantResponse getTenantForDevice(@PathVariable UUID deviceId);
}
