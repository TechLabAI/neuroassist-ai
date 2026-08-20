package com.neuroassist.dash.infrastructure.clients.dto;

/**
 * DTO de integracao: entrada de historico retornada pelo HIST (occurredAt em ISO-8601). public
 * record obrigatorio — proxies JDK nao acessam membros package-private.
 */
public record HistoryEntryResponse(String eventId, String type, String occurredAt) {}
