package com.neuroassist.dash.infrastructure.clients.dto;

import java.util.List;

/**
 * DTO de integracao: resposta do PAC para o catalogo de comandos de um paciente. public record
 * obrigatorio — proxies JDK nao acessam membros package-private.
 */
public record CommandCatalogResponse(List<String> commands, String calibrationState) {}
