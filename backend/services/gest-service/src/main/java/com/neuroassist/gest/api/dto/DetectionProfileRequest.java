package com.neuroassist.gest.api.dto;

import java.util.Map;

/** Request payload for POST /detection-profiles — publish a new versioned detection profile. */
public record DetectionProfileRequest(Map<String, Object> classificationParams) {}
