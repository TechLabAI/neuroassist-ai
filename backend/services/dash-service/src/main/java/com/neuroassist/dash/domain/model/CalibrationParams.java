package com.neuroassist.dash.domain.model;

import java.util.Map;

/** Parametros de calibracao enviados ao PAC. Zero Spring, zero JPA. */
public record CalibrationParams(String sensitivity, String threshold, Map<String, String> extra) {}
