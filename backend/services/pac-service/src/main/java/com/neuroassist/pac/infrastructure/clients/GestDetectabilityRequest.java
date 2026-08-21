package com.neuroassist.pac.infrastructure.clients;

/**
 * Request body for the GEST eligibility check endpoint (INT-PAC-001).
 *
 * <p>GEST evaluates eligibility based on urgency level: for EMERGENCIA, all tenant primitives must
 * be in SUPPORTED state; for any other level, it always returns eligible=true (ADR-006).
 */
public record GestDetectabilityRequest(String urgencyLevel) {}
