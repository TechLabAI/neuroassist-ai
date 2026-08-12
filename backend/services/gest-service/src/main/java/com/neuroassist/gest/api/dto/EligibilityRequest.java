package com.neuroassist.gest.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request payload for POST /eligibility:check — emergency-command guard invoked by PAC. */
public record EligibilityRequest(@NotBlank String urgencyLevel) {}
