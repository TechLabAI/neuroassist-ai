package com.neuroassist.gest.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for the EDGE pairing service. Disabled in poc profile — replaced by
 * EdgePairingPortStub. RULE-06: @FeignClient only in infrastructure.clients.
 */
@Profile("!poc")
@FeignClient(name = "edge-pairing-client", url = "${edge.pairing.url:http://localhost:8090}")
public interface EdgePairingClient {

    /**
     * Checks whether the given deviceId is paired and returns the associated tenant information.
     *
     * @param deviceId the UUID of the edge device to check
     * @return pairing response with tenantId and paired flag
     */
    @GetMapping("/api/v1/edge/devices/{deviceId}/tenant")
    TenantPairingResponse checkPairingAndGetTenant(@PathVariable UUID deviceId);
}
