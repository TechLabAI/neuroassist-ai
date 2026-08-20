package com.neuroassist.dash.infrastructure.clients.dto;

/**
 * DTO de integracao: resposta do PAC para o estado de calibracao de um paciente.
 * public record obrigatorio — proxies JDK nao acessam membros package-private.
 */
public record CalibrationStateResponse(String state) {}
