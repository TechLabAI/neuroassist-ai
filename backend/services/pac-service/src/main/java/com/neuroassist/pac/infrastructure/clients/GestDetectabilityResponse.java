package com.neuroassist.pac.infrastructure.clients;

import java.util.List;

/**
 * Response from the GEST eligibility check endpoint (INT-PAC-001).
 *
 * <p>{@code eligible=true} means the gesture primitive set is ready for emergency dispatch. {@code
 * missingStates} lists primitives not yet in SUPPORTED state (used for advisory messages).
 */
public record GestDetectabilityResponse(boolean eligible, List<String> missingStates) {}
