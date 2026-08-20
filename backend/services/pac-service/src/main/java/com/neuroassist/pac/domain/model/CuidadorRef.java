package com.neuroassist.pac.domain.model;

import java.util.UUID;

/**
 * Domain value object — Cuidador (caregiver) reference resolved from ACS directory (SPEC-10 §5).
 *
 * <p>RULE-01: zero Spring imports. RULE-02: zero JPA imports. Pure POJO record.
 *
 * <p>{@code empresaId} is used to verify the cuidador belongs to the same empresa as the patient
 * being created or updated (cross-empresa rejection guard).
 */
public record CuidadorRef(UUID id, String empresaId) {}
