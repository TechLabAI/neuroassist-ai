package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;

/**
 * Raw JSON response from ACS GET /api/acs/v1/cuidadores/{id}. Infrastructure-level record — used
 * only within infrastructure.clients. Mapped to domain {@link
 * com.neuroassist.pac.domain.model.CuidadorRef} by {@link AcsDirectoryAdapter}.
 */
public record AcsCuidadorResponse(UUID id, String empresaId) {}
