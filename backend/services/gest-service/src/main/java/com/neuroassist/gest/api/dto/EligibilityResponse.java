package com.neuroassist.gest.api.dto;

import java.util.List;

/** Response for the eligibility check. */
public record EligibilityResponse(boolean eligible, List<String> missingStates) {}
