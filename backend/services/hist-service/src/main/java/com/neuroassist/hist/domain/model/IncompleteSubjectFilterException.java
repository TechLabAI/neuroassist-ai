package com.neuroassist.hist.domain.model;

/**
 * Thrown when {@code subjectId} is provided without {@code subjectType}. Maps to HTTP 400 / {@code
 * type: /errors/incomplete-subject-filter}. Zero Spring/JPA imports (RULE-01, RULE-02).
 */
public class IncompleteSubjectFilterException extends HistBadRequestException {

    public IncompleteSubjectFilterException() {
        super(
                "incomplete-subject-filter",
                "subjectId requires subjectType — both must be provided together.");
    }
}
