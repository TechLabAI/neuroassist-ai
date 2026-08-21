package com.neuroassist.dash.infrastructure.clients.dto;

/**
 * DTO de integracao: notificacao nao reconhecida retornada pelo EVNT. public record obrigatorio —
 * proxies JDK nao acessam membros package-private.
 */
public record NotificationPayloadResponse(
        String dispatchId, String tier, String message, String issuedAt) {}
