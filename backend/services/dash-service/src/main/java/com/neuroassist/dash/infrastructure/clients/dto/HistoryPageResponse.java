package com.neuroassist.dash.infrastructure.clients.dto;

import java.util.List;

/**
 * DTO de integracao: pagina de historico retornada pelo HIST. public record obrigatorio — proxies
 * JDK nao acessam membros package-private.
 */
public record HistoryPageResponse(List<HistoryEntryResponse> entries, int page, int totalPages) {}
