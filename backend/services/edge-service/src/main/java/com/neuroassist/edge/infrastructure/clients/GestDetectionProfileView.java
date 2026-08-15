package com.neuroassist.edge.infrastructure.clients;

/**
 * Minimal Feign response view for GEST detection-profile endpoint (EDGE-014). Only the {@code
 * version} field is consumed by edge-service — all other profile fields are opaque to this service.
 * Full response shape lives in gest-service DetectionProfileResponse.
 */
public record GestDetectionProfileView(long version) {}
