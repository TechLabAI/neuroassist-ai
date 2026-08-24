package com.neuroassist.gest.domain.model;

/**
 * Faixa de sensibilidade de detecção. Vocabulário deliberadamente idêntico ao do PAC ({@code
 * ck_comando_sensibilidade CHECK (sensibilidade IN ('baixa','media','alta'))}), para que ligar
 * {@code comando.sensibilidade} a um perfil um dia seja uma correspondência de nome e não uma
 * tradução.
 *
 * <p>Minúsculas sem acento pelo mesmo motivo: é assim que o valor chega e sai do banco dos dois
 * lados.
 *
 * <p>ZERO Spring / ZERO JPA (RULE-01, RULE-02).
 */
public enum Sensibilidade {
    baixa,
    media,
    alta;

    /** Faixa aplicada quando ninguém escolheu uma — a que reproduz o comportamento histórico. */
    public static Sensibilidade padrao() {
        return media;
    }
}
