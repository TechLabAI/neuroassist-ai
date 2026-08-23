package com.neuroassist.edge.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal Feign response view for GEST detection-profile endpoint (EDGE-014). Only the {@code
 * version} field is consumed by edge-service — all other profile fields are opaque to this service.
 * Full response shape lives in gest-service DetectionProfileResponse.
 *
 * <p>{@code ignoreUnknown = true} is stated explicitly rather than left to the Jackson default, so
 * that GEST may add new fields without breaking this consumer no matter how the service-wide
 * deserialization config is set later.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GestDetectionProfileView(long version) {}
