package com.neuroassist.dash.api.dto;

import java.util.List;

/** Response DTO do write-through de recalibração — eco do resultado autoritativo do PAC. */
public record CalibrationProxyResponse(
        String comandoId,
        String sensibilidade,
        String urgencia,
        String estado,
        int calibracaoVersao,
        List<String> warnings) {}
