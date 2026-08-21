package com.neuroassist.pac.domain.model;

import java.util.UUID;

/**
 * Domain value object — Edge device reference resolved from EDGE service (SPEC-10 §6).
 *
 * <p>RULE-01: zero Spring imports. RULE-02: zero JPA imports. Pure POJO record.
 *
 * <p>{@code pacienteId} is null when the device is unassigned; {@code empresaId} is used to verify
 * the device belongs to the same empresa as the patient being registered. {@code empresaId} is a
 * String to allow both UUID and string-alias tenant identifiers.
 */
public record DispositivoEdgeRef(UUID id, UUID pacienteId, String empresaId) {}
