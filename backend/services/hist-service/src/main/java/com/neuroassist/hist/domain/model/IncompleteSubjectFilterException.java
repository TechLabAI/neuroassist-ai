package com.neuroassist.hist.domain.model;

/**
 * Thrown when a history query provides {@code subjectId} without {@code subjectType}.
 * Maps to HTTP 400 / {@code type: /errors/incomplete-subject-filter}.
 * Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class IncompleteSubjectFilterException extends RuntimeException {

    public IncompleteSubjectFilterException() {
        super("subjectId is provided without subjectType. Both are required together.");
    }

    public String errorCode() {
        return "incomplete-subject-filter";
    }
}
