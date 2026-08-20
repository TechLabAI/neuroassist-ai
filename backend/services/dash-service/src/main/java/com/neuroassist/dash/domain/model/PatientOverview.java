package com.neuroassist.dash.domain.model;

/**
 * Overview detalhado de um paciente composto a partir de PAC e EDGE. Zero JPA/Spring.
 */
public record PatientOverview(
        String patientId, CommandCatalogSnapshot catalog, DeviceStatus device) {}
