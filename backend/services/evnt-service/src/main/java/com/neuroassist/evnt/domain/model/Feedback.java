package com.neuroassist.evnt.domain.model;

/**
 * Values written to {@code evento.feedback}.
 *
 * <p>Constants are UPPER_CASE because that is what Flyway's default {@code 'NAO_AVALIADO'} and the
 * application code have always persisted. Using {@link Enum#name()} produces the column value
 * directly: {@code Feedback.NAO_AVALIADO.name()} → {@code "NAO_AVALIADO"}. The old lowercase
 * constants ({@code nao_avaliado}, etc.) were never referenced by any code and are removed here as
 * part of E2E-025.
 */
public enum Feedback {
    NAO_AVALIADO,
    REAL,
    FALSO_POSITIVO
}
