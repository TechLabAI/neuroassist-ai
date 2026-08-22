package com.neuroassist.dash.infrastructure.clients.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO de integração: um registro de auditoria retornado pelo hist-service.
 *
 * <p>Subconjunto de {@code RecordAuditFactResponse}: o painel só precisa do identificador, do tipo
 * de fato e de quando aconteceu. Os campos anteriores ({@code eventId}, {@code type}) não existiam
 * no produtor — os reais são {@code id} e {@code factType} (C-33). O HIST devolve muitos outros
 * campos (cadeia de hash, ator, payload); são ignorados aqui de propósito, e o {@code payload} em
 * particular não deve trafegar para o painel sem necessidade (LGPD).
 *
 * <p>{@code public record} obrigatório — proxies JDK não acessam membros package-private.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record HistoryEntryResponse(String id, String factType, String occurredAt) {}
