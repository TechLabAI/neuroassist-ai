package com.neuroassist.pac.infrastructure.clients;

/**
 * Response from the GEST detectability check endpoint (INT-PAC-001).
 *
 * <p>{@code supported} = true means the gesture combination is ready for detection by the ML model.
 * {@code status} carries a human-readable reason (e.g. "active", "pending-training",
 * "insufficient-data") for logging and advisory messages.
 */
public record GestDetectabilityResponse(boolean supported, String status) {}
