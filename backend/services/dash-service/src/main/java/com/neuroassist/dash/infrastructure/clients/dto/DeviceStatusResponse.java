package com.neuroassist.dash.infrastructure.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO de integração: liveness do dispositivo retornado pelo edge-service.
 *
 * <p>Espelha {@code com.neuroassist.edge.api.dto.LivenessResponse}. A forma anterior tinha um campo
 * {@code degraded} que o EDGE nunca devolveu (C-34); o que o EDGE expõe é {@code status} ({@code
 * online | offline | n/a}) mais a janela de staleness usada para decidi-lo.
 *
 * <p>{@code public record} obrigatório — proxies JDK não acessam membros package-private.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DeviceStatusResponse(
        String deviceId,
        String pacienteId,
        String status,
        String ultimaSincronizacao,
        String versao,
        long stalenessThresholdSeconds,
        String serverTime) {}
