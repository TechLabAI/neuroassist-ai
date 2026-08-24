package com.neuroassist.dash.domain.model;

/**
 * Parâmetros de recalibração enviados ao PAC. Zero Spring, zero JPA.
 *
 * <p><b>Espelha {@code RecalibrateRequest} do PAC campo por campo.</b> A forma anterior — {@code
 * (sensitivity, threshold, extra)} — não correspondia a nada no produtor: o PAC não tem {@code
 * threshold}, não tem mapa {@code extra}, e a sensibilidade é um enum ({@code baixa|media|alta}) e
 * não texto livre.
 *
 * <p>Todos os campos são opcionais no PAC: {@code null} significa "mantém o valor atual".
 */
public record CalibrationParams(
        String urgencia, String sensibilidade, Integer tempoMinGestoMs, Integer tempoMaxGestoMs) {}
