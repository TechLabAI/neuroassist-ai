package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;

/**
 * Raw JSON response from ACS {@code GET /api/v1/acs/directory/tenants/{id}} — mirrors ACS's {@code
 * DirectoryResponse.TenantInfoResponse}. Infrastructure-level record — used only within
 * infrastructure.clients. Mapped to domain {@link com.neuroassist.pac.domain.model.EmpresaRef} by
 * {@link AcsDirectoryAdapter}.
 *
 * <p>Must stay {@code public}: JDK dynamic proxies cannot access a package-private Feign response
 * type.
 */
public record AcsEmpresaResponse(UUID tenantId, String status, String tipo, boolean valid) {}
