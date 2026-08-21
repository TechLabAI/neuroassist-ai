package com.neuroassist.hist.domain.model;

/**
 * Thrown when the submitted {@code factType} is not in the active controlled vocabulary (v1). Maps
 * to HTTP 422 / {@code type: /errors/unknown-fact-type}.
 */
public class UnknownFactTypeException extends HistDomainException {

    public UnknownFactTypeException(String factType) {
        super(
                "unknown-fact-type",
                "factType '" + factType + "' is not in the controlled vocabulary v1.");
    }
}
