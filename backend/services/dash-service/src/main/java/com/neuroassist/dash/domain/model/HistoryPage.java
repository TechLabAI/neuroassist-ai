package com.neuroassist.dash.domain.model;

import java.util.List;

/** Pagina paginada do historico de eventos de um paciente, proveniente do HIST. Zero JPA/Spring. */
public record HistoryPage(List<HistoryEntry> entries, int page, int totalPages) {}
