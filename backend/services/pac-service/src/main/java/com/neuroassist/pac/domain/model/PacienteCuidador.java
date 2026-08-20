package com.neuroassist.pac.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model — PacienteCuidador link.
 *
 * <p>RULE-01/RULE-02: zero Spring/JPA imports. {@code cuidadorResponsavelId} is a cross-service
 * UUID reference to ACS Cuidador — not validated in P2 (ACS client added in P4).
 */
public record PacienteCuidador(UUID pacienteId, UUID cuidadorResponsavelId, Instant createdAt) {}
