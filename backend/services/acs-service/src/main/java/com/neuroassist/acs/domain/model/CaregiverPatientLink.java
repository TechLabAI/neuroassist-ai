package com.neuroassist.acs.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model — CaregiverPatientLink. Stored in t_<tenantId> schema (empresa-scoped). Invariant:
 * no physical DELETE — only status transitions (ACTIVE → REVOKED/SUSPENDED). RULE-01: zero
 * org.springframework imports. RULE-02: zero jakarta.persistence imports.
 */
public record CaregiverPatientLink(
        UUID id,
        UUID tenantId,
        UUID cuidadorId,
        UUID pacienteId,
        LinkStatus status,
        Instant criadoEm,
        Instant revogadoEm) {}
