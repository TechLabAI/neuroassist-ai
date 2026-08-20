package com.neuroassist.acs.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain model — Tenant. Stored in neuroassist_global schema. RULE-01: zero org.springframework
 * imports. RULE-02: zero jakarta.persistence imports.
 */
public record Tenant(
        UUID id,
        String nome,
        TenantTipo tipo,
        TenantStatus status,
        String schemaNome,
        Instant criadoEm) {}
