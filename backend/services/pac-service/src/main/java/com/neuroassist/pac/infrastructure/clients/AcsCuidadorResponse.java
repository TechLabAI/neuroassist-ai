package com.neuroassist.pac.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Raw JSON response from ACS {@code GET /api/v1/acs/caregivers/{id}} — mirrors the subset of ACS's
 * {@code CaregiverResponse} that PAC needs. Infrastructure-level record — used only within
 * infrastructure.clients. Mapped to domain {@link com.neuroassist.pac.domain.model.CuidadorRef} by
 * {@link AcsDirectoryAdapter}.
 *
 * <p>Must stay {@code public}: JDK dynamic proxies cannot access a package-private Feign response
 * type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AcsCuidadorResponse(UUID id, String tenant, String papel, String status) {}
