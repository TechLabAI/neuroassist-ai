package com.neuroassist.dash.infrastructure.clients.dto;

/**
 * DTO de integracao: resposta do PAC apos execucao de calibracao. public record obrigatorio —
 * proxies JDK nao acessam membros package-private.
 */
public record CalibrationResultResponse(
        String commandId, String newCalibrationState, boolean success) {}
