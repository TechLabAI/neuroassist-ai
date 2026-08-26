package com.neuroassist.edge.infrastructure.clients;

import java.util.UUID;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;

/**
 * Feign client for GEST detection-profile service (EDGE-014, P4; updated EDGE-022, P1).
 *
 * <p>Path mirrors GEST {@code DetectionProfileController} mapping: {@code GET
 * /gest-service/detection-profile/devices/{deviceId}}.
 *
 * <p>Authenticated with the EDGE <b>service identity</b> (not the caller's JWT) — see {@link
 * GestServiceIdentityFeignConfig} and {@link ServiceIdentityRequestInterceptor}. GEST requires the
 * authority {@code gest.detection-profile.read}, which a device token never has. This client uses a
 * service token that carries that authority, so the EDGE service acts as the trusted intermediary
 * (principle of least privilege: device → EDGE → GEST, never device → GEST directly).
 *
 * <p><b>Defect C-19 is closed.</b> {@link DeviceProfileController} ({@code GET
 * /api/v1/edge/profile}) now proxies this client for device requests. The device token never
 * reaches GEST; only the service identity does.
 *
 * <p>RULE-06: {@code @FeignClient} only in {@code infrastructure.clients}.
 */
@FeignClient(
        name = "gest-service",
        url = "${neuroassist.gest.url}",
        configuration = GestServiceIdentityFeignConfig.class)
public interface DetectionProfileClient {

    /**
     * Retrieve the active detection profile version pointer for a device (GEST FROZEN §4.2).
     * Returns 200 with a {@code version} field when active, 404 when no profile is published yet.
     * Used by {@link DetectionProfileFeignAdapter} for heartbeat-driven drift detection only.
     *
     * <p>This method does NOT forward {@code If-None-Match} — heartbeat drift detection always
     * needs the current version, never a conditional check.
     */
    @GetMapping("/gest-service/detection-profile/devices/{deviceId}")
    ResponseEntity<GestDetectionProfileView> getActiveProfileForDevice(
            @PathVariable("deviceId") UUID deviceId);

    /**
     * Retrieve the full detection profile for the browser station, with conditional-GET support
     * (EDGE-022, P1). Forwards the {@code If-None-Match} header from the station to GEST so that
     * GEST can return {@code 304 Not Modified} when the station's cached profile is still current.
     *
     * <p>Used exclusively by {@link DeviceProfileFeignAdapter} — do NOT call this method from
     * {@link DetectionProfileFeignAdapter} (heartbeat path must always get the current version).
     *
     * <p>Response status handling in {@link DeviceProfileFeignAdapter}:
     *
     * <ul>
     *   <li>{@code 200} — full profile; body is non-null;
     *   <li>{@code 304} — ETag matched; body is null, no re-download needed;
     *   <li>{@code 404} — device not paired in GEST (should not happen if EDGE verified pairing
     *       first); treated as an ignore-exception in the circuit breaker config.
     * </ul>
     */
    @GetMapping("/gest-service/detection-profile/devices/{deviceId}")
    ResponseEntity<GestDetectionProfileView> getProfileForDeviceConditional(
            @PathVariable("deviceId") UUID deviceId,
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch);
}
