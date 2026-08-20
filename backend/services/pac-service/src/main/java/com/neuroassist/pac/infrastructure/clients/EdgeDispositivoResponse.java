package com.neuroassist.pac.infrastructure.clients;

import java.util.UUID;

/**
 * Raw JSON response from EDGE GET /api/edge/v1/dispositivos/{id}. Infrastructure-level record —
 * used only within infrastructure.clients. Mapped to domain {@link
 * com.neuroassist.pac.domain.model.DispositivoEdgeRef} by {@link EdgeDeviceAdapter}.
 *
 * <p>{@code pacienteId} is null when the device is unassigned.
 */
public record EdgeDispositivoResponse(UUID id, UUID pacienteId, String empresaId) {}
