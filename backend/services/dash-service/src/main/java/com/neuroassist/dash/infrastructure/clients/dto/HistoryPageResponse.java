package com.neuroassist.dash.infrastructure.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * DTO de integração: página de histórico retornada pelo hist-service.
 *
 * <p>Espelha {@code com.neuroassist.hist.api.dto.HistoryQueryResponse} — {@code (items, page, size,
 * total)} — e não a forma anterior {@code (entries, page, totalPages)}, que era uma invenção do
 * consumidor (C-33). Note que o HIST devolve o <b>total de registros</b>, não o total de páginas; a
 * conversão é feita no adapter.
 *
 * <p>{@code public record} obrigatório — proxies JDK não acessam membros package-private.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HistoryPageResponse(
        List<HistoryEntryResponse> items, int page, int size, long total) {}
