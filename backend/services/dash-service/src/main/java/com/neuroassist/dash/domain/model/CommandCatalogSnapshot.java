package com.neuroassist.dash.domain.model;

import java.util.List;

/** Snapshot do catalogo de comandos de um paciente, proveniente do PAC. Zero JPA/Spring. */
public record CommandCatalogSnapshot(List<String> commands, String calibrationState) {}
