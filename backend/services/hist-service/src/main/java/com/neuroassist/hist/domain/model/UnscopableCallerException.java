package com.neuroassist.hist.domain.model;

/**
 * Thrown when a {@code hist:read} caller cannot be resolved to a caregiver, so their patient scope
 * cannot be determined.
 *
 * <p>Replaces returning {@code null}, which downstream meant "no restriction" — a token whose
 * {@code sub} was not a UUID silently escalated a scoped read into a full-tenant one (C-36). What
 * cannot be scoped must be refused, not widened.
 *
 * <p>Maps to HTTP 403. Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class UnscopableCallerException extends RuntimeException {

    public UnscopableCallerException(String subject) {
        super(
                "Caller with hist:read cannot be scoped: token subject '"
                        + subject
                        + "' is not a caregiver id.");
    }

    public String errorCode() {
        return "unscopable-caller";
    }
}
