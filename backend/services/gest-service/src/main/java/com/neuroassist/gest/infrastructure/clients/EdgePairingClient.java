package com.neuroassist.gest.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for the EDGE pairing service. Disabled in poc profile — replaced by
 * EdgePairingPortStub. RULE-06: @FeignClient only in infrastructure.clients.
 *
 * <p><b>The default url used to be {@code http://localhost:8090}</b>, wrong twice over: 8090 is
 * dash-service, and {@code localhost} inside a container is gest-service itself. Every
 * device→tenant resolution — which {@code GET
 * /gest-service/detection-profile/devices/&#123;id&#125;} performs before answering the EDGE
 * heartbeat — therefore went nowhere. Registered as debt in plan-e2e-integration.md §11.2 since
 * Sessão 2.
 */
@Profile("!poc")
@FeignClient(
        name = "edge-pairing-client",
        url = "${edge.pairing.url:${EDGE_SERVICE_URL:http://edge-service:8087}}")
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
