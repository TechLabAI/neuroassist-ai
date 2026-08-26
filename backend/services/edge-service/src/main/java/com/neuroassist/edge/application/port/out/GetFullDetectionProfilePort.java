package com.neuroassist.edge.application.port.out;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port — full detection profile retrieval for the browser station (EDGE-022, P1).
 *
 * <p>Distinct from {@link DetectionProfilePort}, which only returns the profile <em>version
 * pointer</em> used by the heartbeat. This port fetches the complete profile data needed by the
 * station: primitives, thresholds, patientId, and ETag for conditional GET.
 *
 * <p>Three outcomes model the three things GEST can say:
 *
 * <ul>
 *   <li>{@link ProfileResult.Ok} — GEST answered with the full profile;
 *   <li>{@link ProfileResult.NotModified} — GEST answered 304 (ETag matched; no profile change);
 *   <li>{@link ProfileResult.Degraded} — GEST could not be reached. NEVER return this silently —
 *       the service layer must surface it as an explicit error so the station does not run without
 *       a profile (worst failure mode of this product).
 * </ul>
 *
 * <p>All types are defined inside the port interface (application layer). The Feign-facing view
 * ({@code GestDetectionProfileView}) stays exclusively in {@code infrastructure.clients}; the
 * adapter maps it here before returning. RULE-04/05 satisfied.
 */
public interface GetFullDetectionProfilePort {

    /**
     * Fetch the active detection profile for the given device, with optional conditional-GET
     * support.
     *
     * @param deviceId the device requesting its profile
     * @param ifNoneMatch the ETag the device already holds, or {@code null} for an unconditional
     *     GET
     * @return one of the three sealed outcomes
     */
    ProfileResult getProfileForDevice(UUID deviceId, String ifNoneMatch);

    // -------------------------------------------------------------------------
    // Result type hierarchy (sealed)
    // -------------------------------------------------------------------------

    sealed interface ProfileResult
            permits ProfileResult.Ok, ProfileResult.NotModified, ProfileResult.Degraded {

        record Ok(ProfileData profile) implements ProfileResult {}

        record NotModified() implements ProfileResult {}

        record Degraded() implements ProfileResult {}

        static ProfileResult ok(ProfileData p) {
            return new Ok(p);
        }

        static ProfileResult notModified() {
            return new NotModified();
        }

        static ProfileResult degraded() {
            return new Degraded();
        }
    }

    // -------------------------------------------------------------------------
    // Data records (application-layer only — no infrastructure imports)
    // -------------------------------------------------------------------------

    /**
     * All data the station needs from the detection profile: what to detect, with what sensitivity,
     * and for whom.
     */
    record ProfileData(
            UUID pacienteId,
            long version,
            boolean catalogoDegradado,
            String etag,
            ThresholdTableData thresholds,
            List<PrimitiveData> primitives) {}

    /** Three sensitivity bands. */
    record ThresholdTableData(BandData baixa, BandData media, BandData alta) {}

    /** Numeric thresholds for a single sensitivity band. */
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

    /** One active gesture primitive in the profile. */
    record PrimitiveData(
            UUID tipoGestoId,
            String nome,
            String engineFeatureRef,
            String sensibilidade,
            String origem) {}
}
