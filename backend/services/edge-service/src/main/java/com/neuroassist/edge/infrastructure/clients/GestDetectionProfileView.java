package com.neuroassist.edge.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.util.UUID;

/**
 * Feign response view for GEST {@code /gest-service/detection-profile/devices/{deviceId}}.
 *
 * <p>Expanded in EDGE-022 (P1) to carry the full set of fields the browser station needs:
 * primitives, thresholds, pacienteId, ETag, and degradation flag. The original {@code version}
 * field is preserved as-is so {@link DetectionProfileFeignAdapter} — which only reads {@code
 * version()} — continues to compile and run without modification.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} is stated explicitly on the outer record
 * <em>and</em> on each nested record: GEST may add new fields to any level without breaking this
 * consumer, no matter how the service-wide deserialization config evolves.
 *
 * <p><b>Defect C-19 is closed.</b> {@link DeviceProfileController} ({@code GET
 * /api/v1/edge/profile}) now serves this view to the browser station via {@link
 * DetectionProfileClient} (service identity). Device tokens no longer need {@code
 * gest.detection-profile.read} — the EDGE service acts as the intermediary as intended (principle
 * of least privilege: device talks to EDGE; only EDGE talks to GEST).
 *
 * <p>This class must stay exclusively in {@code infrastructure.clients} — no class outside this
 * package should import it. The application-layer port ({@link
 * com.neuroassist.edge.application.port.out.GetFullDetectionProfilePort}) defines its own data
 * records; {@link DeviceProfileFeignAdapter} maps this view to those records.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GestDetectionProfileView(
        long version,
        UUID pacienteId,
        boolean catalogoDegradado,
        String etag,
        GestDetectionThresholdTableView thresholds,
        List<GestProfilePrimitiveView> primitives) {

    /** Three sensitivity bands — mirrors {@code DetectionThresholdTable} in gest-service. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GestDetectionThresholdTableView(
            GestDetectionBandView baixa, GestDetectionBandView media, GestDetectionBandView alta) {}

    /**
     * Numeric thresholds for one sensitivity band — mirrors {@code DetectionThresholds} in
     * gest-service. All nine fields must match the exact JSON property names produced by the
     * serializer in gest-service (record component names → camelCase by default in Jackson).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GestDetectionBandView(
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
     * One active gesture in the profile — mirrors {@code DetectionProfilePrimitiveResponse} in
     * gest-service. {@code sensibilidade} and {@code origem} are {@code String} (not enum) to avoid
     * any enum-name mismatch across service boundaries: the values are forwarded as-is to the
     * station.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GestProfilePrimitiveView(
            UUID tipoGestoId,
            String nome,
            String engineFeatureRef,
            String sensibilidade,
            String origem) {}
}
