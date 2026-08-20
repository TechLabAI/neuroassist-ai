package com.neuroassist.dash.domain.model;

/** Entrada resumida de um paciente na visao home do painel do cuidador. Zero JPA/Spring. */
public record PatientSummaryEntry(
        String patientId, String name, boolean hasActiveEvent, String deviceStatus) {}
