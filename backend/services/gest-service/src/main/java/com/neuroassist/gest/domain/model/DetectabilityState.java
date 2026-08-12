package com.neuroassist.gest.domain.model;

/**
 * Detectability lifecycle states for a TipoGesto primitive. ADR-006 defines the allowed
 * transitions: REGISTERED→PENDING_TRAINING, PENDING_TRAINING→SUPPORTED, SUPPORTED→DEPRECATED,
 * REGISTERED→DEPRECATED, PENDING_TRAINING→DEPRECATED. No regression is permitted.
 */
public enum DetectabilityState {
    REGISTERED,
    PENDING_TRAINING,
    SUPPORTED,
    DEPRECATED
}
