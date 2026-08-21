package com.neuroassist.edge.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client for GEST detection-profile service (EDGE-014, P4). Resolves the active detection
 * profile version for a given device. RULE-06: @FeignClient only in infrastructure.clients.
 *
 * <p>Path mirrors GEST DetectionProfileController mapping: GET
 * /gest-service/detection-profile/devices/{deviceId}
 *
 * <p>Authenticated with the EDGE <b>service identity</b> (not the caller's JWT) — see {@link
 * GestServiceIdentityFeignConfig} and {@link ServiceIdentityRequestInterceptor}. GEST requires the
 * authority {@code gest.detection-profile.read}, which a device token never has (defect C-19).
 */
@FeignClient(
        name = "gest-service",
        url = "${neuroassist.gest.url}",
        configuration = GestServiceIdentityFeignConfig.class)
public interface DetectionProfileClient {

    /**
     * Retrieve active detection profile for a device (GEST FROZEN §4.2). Returns 200 with version
     * when active, 404 when no profile is published yet. The Resilience4j circuit breaker wraps
     * this call in {@link DetectionProfileFeignAdapter}.
     */
    @GetMapping("/gest-service/detection-profile/devices/{deviceId}")
    ResponseEntity<GestDetectionProfileView> getActiveProfileForDevice(
            @PathVariable("deviceId") UUID deviceId);
}
