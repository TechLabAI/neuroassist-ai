package com.neuroassist.dash.infrastructure.clients.dto;

/**
 * DTO de integracao: status de liveness do dispositivo retornado pelo EDGE.
 * public record obrigatorio — proxies JDK nao acessam membros package-private.
 */
public record DeviceStatusResponse(
        String deviceId,
        String status,
        boolean degraded) {}
