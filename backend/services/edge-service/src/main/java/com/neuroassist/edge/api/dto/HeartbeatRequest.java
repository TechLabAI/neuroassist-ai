package com.neuroassist.edge.api.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import java.util.UUID;

/**
 * Request body for POST /api/v1/edge/heartbeat.
 *
 * <p>Anti-spoofing invariant #2 (ADR-005): the canonical device identity comes exclusively from the
 * authenticated device JWT ({@code sub} claim). The optional {@code deviceId} field is tolerated in
 * the body only to enable explicit identity-mismatch rejection: if present and differing from the
 * JWT device, the controller rejects with 403 (edge.identity-mismatch). If absent (the normal
 * case), it is ignored.
 *
 * <p><b>Privacy invariant #6</b>: no biometric or media data (image, audio, video) may appear in
 * this body. The rule is enforced <i>here</i>, on this one payload, rather than by the global
 * Jackson {@code fail-on-unknown-properties} switch: flipping that globally would turn every
 * request carrying an extra field — on any EDGE endpoint, from any client — into a 400, which is a
 * far wider contract change than the invariant calls for.
 *
 * <p>Note that {@code @JsonIgnoreProperties(ignoreUnknown = false)} would <b>not</b> achieve this:
 * it only declines to suppress unknown fields, leaving the actual failure to the global {@code
 * FAIL_ON_UNKNOWN_PROPERTIES} feature, which Spring Boot disables by default. The
 * {@code @JsonAnySetter} catch-all below collects anything undeclared regardless of global config,
 * and the canonical constructor rejects it — Jackson wraps the exception into {@code
 * HttpMessageNotReadableException}, which {@code GlobalExceptionHandler} renders as 400.
 *
 * <p>No tenantId field — derived from the device JWT via TenantContextFilter.
 */
public record HeartbeatRequest(
        @NotBlank String agentVersion,
        Long runningProfileVersion,
        UUID deviceId,
        @JsonAnySetter Map<String, Object> unknownFields) {

    public HeartbeatRequest {
        if (unknownFields != null && !unknownFields.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unrecognized field(s) in heartbeat body: "
                            + unknownFields.keySet()
                            + " (privacy invariant #6: no media or undeclared fields)");
        }
    }

    /** Convenience constructor for callers that never carry undeclared fields. */
    public HeartbeatRequest(String agentVersion, Long runningProfileVersion, UUID deviceId) {
        this(agentVersion, runningProfileVersion, deviceId, null);
    }
}
