package com.neuroassist.gest.api.dto;

import jakarta.validation.constraints.NotBlank;

/** Request payload for POST /tipos-gesto/{id}/transitions — trigger a lifecycle transition. */
public record TransitionRequest(
        @NotBlank String toState, String actor, String reason, String engineFeatureRef) {}
