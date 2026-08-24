package com.neuroassist.dash.api.dto;

/**
 * Request DTO para write-through de recalibração no PAC.
 *
 * <p><b>Espelha {@code RecalibrateRequest} do PAC.</b> A forma anterior — {@code (sensitivity,
 * threshold, extra)} — não correspondia a campo nenhum do produtor: o PAC não tem {@code threshold}
 * nem mapa {@code extra}, e a sensibilidade é um enum ({@code baixa|media|alta}).
 *
 * <p>Nenhum campo é obrigatório: no PAC, {@code null} significa "mantém o valor atual", e uma
 * recalibração que só muda a urgência é legítima. Por isso não há {@code @NotBlank} aqui — a
 * validação de valor pertence ao dono do dado, e o painel repassa a recusa do PAC intacta.
 */
public record CalibrationProxyRequest(
        String urgencia, String sensibilidade, Integer tempoMinGestoMs, Integer tempoMaxGestoMs) {}
