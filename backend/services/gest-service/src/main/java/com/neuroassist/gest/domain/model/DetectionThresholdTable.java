package com.neuroassist.gest.domain.model;

/**
 * As três faixas de sensibilidade de um perfil publicado. ZERO Spring / ZERO JPA (RULE-01,
 * RULE-02).
 *
 * <p>Cada faixa é coerente consigo mesma por construção ({@link DetectionThresholds}). O que esta
 * tabela acrescenta é a coerência <b>entre</b> faixas: um perfil em que "alta" detecta menos que
 * "baixa" é um perfil sem sentido, e sem esta validação seria publicável — a cuidadora escolheria
 * "alta" e o gesto ficaria mais difícil de disparar.
 *
 * <p>EAR e BRI andam em sentidos opostos: EAR cai quando o olho fecha, então detectar mais fácil é
 * um limiar MAIOR; BRI sobe quando a sobrancelha levanta, então detectar mais fácil é um limiar
 * MENOR. Frames de sustentação e janela do piscar duplo seguem a mesma leitura — mais sensível
 * exige menos sustentação e concede mais tempo.
 */
public record DetectionThresholdTable(
        DetectionThresholds baixa, DetectionThresholds media, DetectionThresholds alta) {

    public DetectionThresholdTable {
        if (baixa == null || media == null || alta == null) {
            throw new IllegalArgumentException(
                    "A tabela de limiares exige as três faixas (baixa, media, alta)");
        }
        ascending("earClose", baixa.earClose(), media.earClose(), alta.earClose());
        ascending("earOpen", baixa.earOpen(), media.earOpen(), alta.earOpen());
        ascending("earOtherMax", baixa.earOtherMax(), media.earOtherMax(), alta.earOtherMax());
        ascending(
                "doubleBlinkWindowMs",
                baixa.doubleBlinkWindowMs(),
                media.doubleBlinkWindowMs(),
                alta.doubleBlinkWindowMs());

        descending("briRaiseOn", baixa.briRaiseOn(), media.briRaiseOn(), alta.briRaiseOn());
        descending("briRaiseOff", baixa.briRaiseOff(), media.briRaiseOff(), alta.briRaiseOff());
        descending("briOtherMax", baixa.briOtherMax(), media.briOtherMax(), alta.briOtherMax());
        descending(
                "blinkMinFrames",
                baixa.blinkMinFrames(),
                media.blinkMinFrames(),
                alta.blinkMinFrames());
        descending(
                "browMinFrames", baixa.browMinFrames(), media.browMinFrames(), alta.browMinFrames());
    }

    /**
     * Os valores históricos. {@code media} é, valor por valor, o que os agentes de borda vinham
     * usando como constante de código — de propósito: adotar o perfil não muda comportamento
     * nenhum até alguém escolher outra faixa.
     *
     * <p>Estes números têm um espelho em {@code backend/edge_agent_core.py} e são vigiados por
     * {@code backend/scripts/check_edge_agent_drift.py} (E2E-032/E2E-033). Mudar um lado sem o
     * outro reprova no CI.
     */
    public static DetectionThresholdTable defaults() {
        return new DetectionThresholdTable(
                new DetectionThresholds(0.19, 0.24, 0.27, 3, 1500, 0.34, 0.26, 0.29, 7),
                new DetectionThresholds(0.22, 0.27, 0.30, 2, 2000, 0.30, 0.22, 0.25, 5),
                new DetectionThresholds(0.25, 0.30, 0.33, 2, 2500, 0.26, 0.18, 0.21, 3));
    }

    /** Os limiares da faixa pedida; {@code null} cai na faixa padrão. */
    public DetectionThresholds forBand(Sensibilidade band) {
        return switch (band == null ? Sensibilidade.padrao() : band) {
            case baixa -> baixa;
            case media -> media;
            case alta -> alta;
        };
    }

    private static void ascending(String field, double low, double mid, double high) {
        if (!(low <= mid && mid <= high)) {
            throw new IllegalArgumentException(
                    "Tabela de limiares incoerente: "
                            + field
                            + " deveria crescer de baixa para alta (recebido "
                            + low + " / " + mid + " / " + high
                            + ") — nessa métrica, valor maior detecta mais fácil");
        }
    }

    private static void descending(String field, double low, double mid, double high) {
        if (!(low >= mid && mid >= high)) {
            throw new IllegalArgumentException(
                    "Tabela de limiares incoerente: "
                            + field
                            + " deveria decrescer de baixa para alta (recebido "
                            + low + " / " + mid + " / " + high
                            + ") — nessa métrica, valor menor detecta mais fácil");
        }
    }
}
