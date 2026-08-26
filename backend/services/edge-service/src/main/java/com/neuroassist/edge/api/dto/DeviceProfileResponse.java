package com.neuroassist.edge.api.dto;

import java.util.List;
import java.util.UUID;

/**
 * API response for {@code GET /api/v1/edge/profile} (EDGE-022, P1).
 *
 * <p>Carries everything the browser station needs in a single response:
 *
 * <ul>
 *   <li>{@code pacienteId} — who to observe; used in {@code POST /api/v1/reports};
 *   <li>{@code primitives[].engineFeatureRef} — what gestures to detect;
 *   <li>{@code thresholds} + {@code primitives[].sensibilidade} — at what sensitivity;
 *   <li>{@code etag} — for subsequent conditional GET ({@code If-None-Match}).
 * </ul>
 *
 * <p>No infrastructure types are imported here (RULE-04). All nested types are API-layer records.
 *
 * <p>RULE-04: {@code api.*} must not depend on {@code infrastructure.*}.
 */
public record DeviceProfileResponse(
        UUID pacienteId,
        long version,
        boolean catalogoDegradado,
        String etag,
        ThresholdTableResponse thresholds,
        List<PrimitiveResponse> primitives) {

    /** Three sensitivity bands (baixa / media / alta). */
    public record ThresholdTableResponse(
            BandResponse baixa, BandResponse media, BandResponse alta) {}

    /**
     * Numeric detection thresholds for one sensitivity band. Field names are camelCase to match the
     * GEST JSON serialization and the station's detector expectations.
     */
    public record BandResponse(
            double earClose,
            double earOpen,
            double earOtherMax,
            int blinkMinFrames,
            int doubleBlinkWindowMs,
            double briRaiseOn,
            double briRaiseOff,
            double briOtherMax,
            int browMinFrames) {}

    /**
     * One active gesture primitive. {@code sensibilidade} and {@code origem} are {@code String}
     * (not enum) because the station consumes them as-is and the vocabulary is owned by GEST.
     */
    public record PrimitiveResponse(
            UUID tipoGestoId,
            String nome,
            String engineFeatureRef,
            String sensibilidade,
            String origem) {}
}
