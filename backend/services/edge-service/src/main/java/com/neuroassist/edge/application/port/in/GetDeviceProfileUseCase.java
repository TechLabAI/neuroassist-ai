package com.neuroassist.edge.application.port.in;

import java.util.List;
import java.util.UUID;

/**
 * Inbound port — device detection profile retrieval (EDGE-022, P1).
 *
 * <p>{@code deviceId} is extracted by the controller from the authenticated device token ({@code
 * sub} claim) — never from path, query string, or request body. This closes the spoofing vector by
 * construction (same pattern as {@code HeartbeatUseCase}, ADR-005 §2).
 *
 * <p>Two outcomes are surfaced to the controller:
 *
 * <ul>
 *   <li>{@link DeviceProfileResult.Ok} — full profile available; controller builds a 200 response
 *       with ETag + Cache-Control headers;
 *   <li>{@link DeviceProfileResult.NotModified} — client ETag matched; controller returns 304.
 * </ul>
 *
 * <p>Error conditions (device not paired, GEST unavailable) are signalled via {@link
 * com.neuroassist.edge.shared.error.CodedException} and handled by {@link
 * com.neuroassist.edge.api.exception.GlobalExceptionHandler}.
 */
public interface GetDeviceProfileUseCase {

    /**
     * Retrieve the detection profile for the authenticated device.
     *
     * @param deviceId from the device JWT {@code sub} claim (anti-spoofing)
     * @param ifNoneMatch the ETag the station already holds, or {@code null}
     * @return {@link DeviceProfileResult.Ok} or {@link DeviceProfileResult.NotModified}
     * @throws com.neuroassist.edge.shared.error.CodedException with code {@code
     *     edge.device-not-found} if the device does not exist in this tenant
     * @throws com.neuroassist.edge.shared.error.CodedException with code {@code
     *     edge.device-not-paired} if the device exists but is not paired
     * @throws com.neuroassist.edge.shared.error.CodedException with code {@code
     *     edge.profile-unavailable} if GEST could not be reached
     */
    DeviceProfileResult getDeviceProfile(UUID deviceId, String ifNoneMatch);

    // -------------------------------------------------------------------------
    // Result type hierarchy (sealed)
    // -------------------------------------------------------------------------

    sealed interface DeviceProfileResult
            permits DeviceProfileResult.Ok, DeviceProfileResult.NotModified {

        record Ok(ProfileData profile) implements DeviceProfileResult {}

        record NotModified() implements DeviceProfileResult {}

        static DeviceProfileResult ok(ProfileData p) {
            return new Ok(p);
        }

        static DeviceProfileResult notModified() {
            return new NotModified();
        }
    }

    // -------------------------------------------------------------------------
    // Use-case data records (application layer, no infrastructure types)
    // -------------------------------------------------------------------------

    record ProfileData(
            UUID pacienteId,
            long version,
            boolean catalogoDegradado,
            String etag,
            ThresholdData thresholds,
            List<PrimitiveData> primitives) {}

    record ThresholdData(BandData baixa, BandData media, BandData alta) {}

    record BandData(
            double earClose,
            double earOpen,
            double earOtherMax,
            int blinkMinFrames,
            int doubleBlinkWindowMs,
            double briRaiseOn,
            double briRaiseOff,
            double briOtherMax,
            int browMinFrames) {}

    record PrimitiveData(
            UUID tipoGestoId,
            String nome,
            String engineFeatureRef,
            String sensibilidade,
            String origem) {}
}
