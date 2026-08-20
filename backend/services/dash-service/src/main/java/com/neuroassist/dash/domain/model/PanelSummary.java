package com.neuroassist.dash.domain.model;

import java.util.List;

/**
 * Resumo do painel do cuidador composto a partir de ACS, EVNT e EDGE. Zero JPA/Spring.
 */
public record PanelSummary(List<PatientSummaryEntry> patients) {}
