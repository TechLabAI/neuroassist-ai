package com.neuroassist.acs.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO — Caregiver-Patient Link. LGPD: no credential or private fields exposed. */
public record CaregiverPatientLinkResponse(
        UUID id,
        UUID tenant,
        UUID cuidadorId,
        UUID pacienteId,
        String status,
        Instant criadoEm,
        Instant revogadoEm) {}
