package com.neuroassist.acs.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model — Cuidador (Caregiver). RULE-01: zero org.springframework imports. RULE-02: zero
 * jakarta.persistence imports.
 */
public record Cuidador(
        UUID id,
        UUID tenantId,
        String nome,
        String login,
        String credentialHash,
        CuidadorPapel papel,
        CuidadorStatus status,
        Instant criadoEm,
        Instant atualizadoEm) {}
