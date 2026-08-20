package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;

/**
 * Raw JSON response from ACS GET /api/acs/v1/empresas/{id}. Infrastructure-level record — used only
 * within infrastructure.clients. Mapped to domain {@link
 * com.neuroassist.pac.domain.model.EmpresaRef} by {@link AcsDirectoryAdapter}.
 */
record AcsEmpresaResponse(UUID id, boolean valid) {}
