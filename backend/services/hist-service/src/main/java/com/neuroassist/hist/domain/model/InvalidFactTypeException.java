package com.neuroassist.hist.domain.model;

/**
 * Thrown when the submitted {@code factType} is not present in the configured {@code
 * hist.fact-types.allowed} list (HIST-023 / SPEC-02). Extends {@link UnknownFactTypeException} so
 * existing exception handlers and tests remain backward-compatible.
 *
 * <p>Maps to HTTP 422 / {@code type: /errors/unknown-fact-type}.
 */
public class InvalidFactTypeException extends UnknownFactTypeException {

    public InvalidFactTypeException(String factType) {
        super(factType);
    }
}
