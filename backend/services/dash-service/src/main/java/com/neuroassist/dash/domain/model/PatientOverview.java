package com.neuroassist.dash.domain.model;

/**
 * Overview detalhado de um paciente, composto a partir de PAC e EDGE. Zero JPA/Spring.
 *
 * @param name nome real do paciente ({@code PacienteResponse.nome}). Aqui nunca é nulo: o PAC é
 *     dependência <i>hard</i> deste caso de uso — se ele não responde, a resposta é 503 e não um
 *     overview pela metade.
 */
public record PatientOverview(
        String patientId, String name, CommandCatalogSnapshot catalog, DeviceStatus device) {}
