package com.neuroassist.gest.domain.model;

/**
 * Conjunto completo de limiares de uma faixa de sensibilidade — o que o agente de borda precisa
 * para decidir se um gesto aconteceu. ZERO Spring / ZERO JPA (RULE-01, RULE-02).
 *
 * <p>Substitui o {@code Map<String,Object> classificationParams}, que aceitava qualquer coisa:
 * {@code {"earClose":"muito"}} era persistido e servido sem uma reclamação. Um perfil incoerente
 * não falha no publish — falha na casa do paciente, quando um alarme não sai.
 *
 * <p><b>EAR</b> (Eye Aspect Ratio) cai quando o olho fecha; <b>BRI</b> (Brow Raise Index) sobe
 * quando a sobrancelha levanta. Por isso os dois andam em sentidos opostos conforme a
 * sensibilidade: detectar mais fácil é um {@code earClose} MAIOR e um {@code briRaiseOn} MENOR.
 *
 * @param earClose abaixo disto o olho conta como fechado
 * @param earOpen acima disto conta como claramente aberto (histerese contra tremor)
 * @param earOtherMax num piscar individual, o OUTRO olho tem de estar acima disto
 * @param blinkMinFrames frames consecutivos fechados para valer piscada, e não ruído de frame
 * @param doubleBlinkWindowMs janela para duas piscadas contarem como piscar duplo
 * @param briRaiseOn acima disto a sobrancelha conta como levantada
 * @param briRaiseOff abaixo disto conta como relaxada; a folga até {@code briRaiseOn} é a histerese
 * @param briOtherMax num levantamento individual, o lado oposto tem de estar abaixo disto
 * @param browMinFrames frames de sustentação — a sobrancelha sobe e desce sozinha ao falar
 */
public record DetectionThresholds(
        double earClose,
        double earOpen,
        double earOtherMax,
        int blinkMinFrames,
        int doubleBlinkWindowMs,
        double briRaiseOn,
        double briRaiseOff,
        double briOtherMax,
        int browMinFrames) {

    public DetectionThresholds {
        require(earClose > 0 && earClose < earOpen, "earClose deve ser > 0 e < earOpen", earClose);
        require(earOpen <= earOtherMax, "earOpen deve ser <= earOtherMax", earOpen);
        require(earOtherMax < 1.0, "earOtherMax deve ser < 1.0", earOtherMax);
        require(blinkMinFrames >= 1, "blinkMinFrames deve ser >= 1", blinkMinFrames);
        require(doubleBlinkWindowMs > 0, "doubleBlinkWindowMs deve ser > 0", doubleBlinkWindowMs);

        // briRaiseOff <= briOtherMax < briRaiseOn: o lado oposto de um levantamento individual
        // precisa estar num patamar que ainda não conta como levantado.
        require(briRaiseOff > 0, "briRaiseOff deve ser > 0", briRaiseOff);
        require(briRaiseOff <= briOtherMax, "briRaiseOff deve ser <= briOtherMax", briRaiseOff);
        require(briOtherMax < briRaiseOn, "briOtherMax deve ser < briRaiseOn", briOtherMax);
        require(browMinFrames >= 1, "browMinFrames deve ser >= 1", browMinFrames);
    }

    private static void require(boolean condition, String rule, Number offending) {
        if (!condition) {
            throw new IllegalArgumentException(
                    "Limiares de detecção incoerentes: "
                            + rule
                            + " (valor recebido: "
                            + offending
                            + ")");
        }
    }
}
