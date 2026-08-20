package com.neuroassist.pac.domain.model;

import java.util.UUID;

/**
 * Domain value object — Empresa reference resolved from ACS directory (SPEC-10 §5).
 *
 * <p>RULE-01: zero Spring imports. RULE-02: zero JPA imports. Pure POJO record.
 *
 * <p>{@code valid} reflects the ACS response field; an empresa with {@code valid = false} should be
 * rejected at the application layer before persisting.
 */
public record EmpresaRef(UUID id, boolean valid) {}
