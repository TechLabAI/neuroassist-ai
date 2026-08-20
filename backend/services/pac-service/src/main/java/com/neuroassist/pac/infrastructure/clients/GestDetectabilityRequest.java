package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;

/**
 * Request body for the GEST detectability check endpoint (INT-PAC-001).
 *
 * <p>Carries both primitives — secondary is optional (null = single-gesture command).
 */
public record GestDetectabilityRequest(UUID primitivoPrincipalId, UUID primitivoSecundarioId) {}
