package com.neuroassist.edge.api.controller;

import com.neuroassist.edge.api.dto.DeviceProfileResponse;
import com.neuroassist.edge.application.port.in.GetDeviceProfileUseCase;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Device detection profile endpoint (EDGE-022, P1).
 *
 * <p>{@code GET /api/v1/edge/profile} — serves the active detection profile to the browser station.
 * Requires the {@code edge.device} authority (issued exclusively to device tokens by {@code
 * DeviceTokenService}). User tokens (cuidadora, coordenadora) are rejected with 403 — this endpoint
 * is not a second door for clinical data.
 *
 * <p><b>Anti-spoofing by construction.</b> {@code deviceId} is extracted from the authenticated
 * device token's {@code sub} claim via {@code auth.getName()} — the same pattern as {@link
 * HeartbeatController}. There is no {@code deviceId} path variable, query parameter, or request
 * body field. An attacker who cannot forge a token cannot request another device's profile.
 *
 * <p><b>Conditional GET (ETag / 304).</b> The {@code If-None-Match} header from the station is
 * forwarded through the use case to GEST. When GEST answers 304, this controller returns 304 with
 * no body. On 200, the {@code ETag} and {@code Cache-Control: max-age=60} headers from GEST's
 * response are forwarded to the station.
 *
 * <p>RULE-04: no imports from {@code infrastructure.*} in this class.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/edge/profile")
@RequiredArgsConstructor
public class DeviceProfileController {

    private final GetDeviceProfileUseCase getDeviceProfileUseCase;

    /**
     * Retrieve the active detection profile for the authenticated device.
     *
     * @param auth Spring Security authentication — {@code sub} claim is the device UUID
     * @param ifNoneMatch optional ETag the station already holds; forwarded for conditional GET
     * @return 200 with profile, 304 when ETag matched, 403 when not a device token or not paired
     */
    @GetMapping
    @PreAuthorize("hasAuthority('edge.device')")
    public ResponseEntity<DeviceProfileResponse> getProfile(
            Authentication auth,
            @RequestHeader(name = "If-None-Match", required = false) String ifNoneMatch) {

        // deviceId from device JWT sub claim — never from path, query or body (ADR-005 §2).
        UUID deviceId = extractDeviceId(auth);
        log.debug(
                "GET /api/v1/edge/profile — deviceId='{}' ifNoneMatch='{}'", deviceId, ifNoneMatch);

        GetDeviceProfileUseCase.DeviceProfileResult result =
                getDeviceProfileUseCase.getDeviceProfile(deviceId, ifNoneMatch);

        return switch (result) {
            case GetDeviceProfileUseCase.DeviceProfileResult.NotModified ignored ->
                    ResponseEntity.status(HttpStatus.NOT_MODIFIED).build();
            case GetDeviceProfileUseCase.DeviceProfileResult.Ok ok -> {
                DeviceProfileResponse body = mapToResponse(ok.profile());
                yield ResponseEntity.ok()
                        .header(HttpHeaders.ETAG, body.etag())
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=60")
                        .body(body);
            }
        };
    }

    /**
     * Extract deviceId from the authenticated device token. Device JWT {@code sub} claim = deviceId
     * (api-contract §6.3). Mirrors the same logic in {@link HeartbeatController}.
     */
    private static UUID extractDeviceId(Authentication auth) {
        if (auth == null || auth.getName() == null) {
            throw new IllegalStateException("No authenticated principal — device token required");
        }
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException e) {
            String name = auth.getName();
            if (name.startsWith("edge-")) {
                return UUID.fromString(name.substring(5));
            }
            throw new IllegalStateException("Cannot derive deviceId from principal: " + name, e);
        }
    }

    // -------------------------------------------------------------------------
    // Mapping: use-case result data → API DTO
    // -------------------------------------------------------------------------

    private static DeviceProfileResponse mapToResponse(GetDeviceProfileUseCase.ProfileData data) {
        return new DeviceProfileResponse(
                data.pacienteId(),
                data.version(),
                data.catalogoDegradado(),
                data.etag(),
                mapThresholds(data.thresholds()),
                mapPrimitives(data.primitives()));
    }

    private static DeviceProfileResponse.ThresholdTableResponse mapThresholds(
            GetDeviceProfileUseCase.ThresholdData src) {
        if (src == null) {
            return null;
        }
        return new DeviceProfileResponse.ThresholdTableResponse(
                mapBand(src.baixa()), mapBand(src.media()), mapBand(src.alta()));
    }

    private static DeviceProfileResponse.BandResponse mapBand(
            GetDeviceProfileUseCase.BandData src) {
        if (src == null) {
            return null;
        }
        return new DeviceProfileResponse.BandResponse(
                src.earClose(),
                src.earOpen(),
                src.earOtherMax(),
                src.blinkMinFrames(),
                src.doubleBlinkWindowMs(),
                src.briRaiseOn(),
                src.briRaiseOff(),
                src.briOtherMax(),
                src.browMinFrames());
    }

    private static List<DeviceProfileResponse.PrimitiveResponse> mapPrimitives(
            List<GetDeviceProfileUseCase.PrimitiveData> src) {
        if (src == null) {
            return List.of();
        }
        return src.stream()
                .map(
                        p ->
                                new DeviceProfileResponse.PrimitiveResponse(
                                        p.tipoGestoId(),
                                        p.nome(),
                                        p.engineFeatureRef(),
                                        p.sensibilidade(),
                                        p.origem()))
                .toList();
    }
}
