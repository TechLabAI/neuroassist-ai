package com.neuroassist.dash.domain.model;

/** Resultado de calibracao retornado pelo PAC. Zero Spring, zero JPA. */
public record CalibrationResult(String commandId, String newCalibrationState, boolean success) {}
