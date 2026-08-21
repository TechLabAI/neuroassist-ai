package com.neuroassist.dash.api.dto;

/** Response DTO para write-through de calibracao (eco do resultado do PAC). */
public record CalibrationProxyResponse(
        String commandId, String newCalibrationState, boolean success) {}
