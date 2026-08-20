package com.neuroassist.acs.api.dto;

import java.time.Instant;
import java.util.UUID;

/** Response DTO — Tenant. LGPD: no credential or private fields exposed. */
public record TenantResponse(
        UUID id, String nome, String tipo, String status, String schemaNome, Instant criadoEm) {}
