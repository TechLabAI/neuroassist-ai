package com.neuroassist.dash.infrastructure.clients.dto;

import java.util.Map;

/**
 * DTO de integracao: payload de requisicao de calibracao enviado ao PAC.
 * public record obrigatorio — proxies JDK nao acessam membros package-private.
 */
public record CalibrationRequestBody(
        String sensitivity,
        String threshold,
        Map<String, String> extra) {}
