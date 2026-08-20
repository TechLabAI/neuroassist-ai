package com.neuroassist.dash.domain.model;

import java.time.Instant;

/** Entrada do historico de eventos de um paciente, proveniente do HIST. Zero JPA/Spring. */
public record HistoryEntry(String eventId, String type, Instant occurredAt) {}
