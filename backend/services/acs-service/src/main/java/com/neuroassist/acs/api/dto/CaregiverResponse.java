package com.neuroassist.acs.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO — Caregiver. LGPD: no credentialHash, no senha, no privateKey fields exposed. */
public record CaregiverResponse(
        UUID id,
        UUID tenant,
        String nome,
        String login,
        String papel,
        String status,
        Instant criadoEm) {}
