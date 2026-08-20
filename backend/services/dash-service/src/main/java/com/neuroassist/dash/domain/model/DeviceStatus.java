package com.neuroassist.dash.domain.model;

/** Estado de liveness do dispositivo edge associado ao paciente. Zero JPA/Spring. */
public record DeviceStatus(String deviceId, String status, boolean degraded) {}
