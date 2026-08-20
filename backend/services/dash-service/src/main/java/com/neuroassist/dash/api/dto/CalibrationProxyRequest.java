package com.neuroassist.dash.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

/**
 * Request DTO para write-through de calibracao via PAC.
 */
public record CalibrationProxyRequest(
        @NotBlank String sensitivity,
        @NotBlank String threshold,
        Map<String, String> extra) {}
