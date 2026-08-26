package com.neuroassist.pac.infrastructure.clients;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.UUID;

/**
 * Raw JSON response from EDGE {@code GET /api/v1/edge/devices/{id}} — mirrors the subset of
 * edge-service's {@code DeviceResponse} that PAC needs.
 *
 * <p>PAC previously declared {@code (id, pacienteId, empresaId)} against the path {@code
 * /api/edge/v1/dispositivos/{id}}, which does not exist on edge-service: every device validation
 * answered 404 → {@code pac.dispositivo.not-found} (422), so no patient could ever be registered
 * with a paired device (E2E Sessão 3, C-22). Field names must match EDGE verbatim — {@code
 * deviceId} and {@code tenantRef}, not {@code id} and {@code empresaId}.
 *
 * <p>Must stay {@code public}: JDK dynamic proxies cannot access a package-private Feign response
 * type.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record EdgeDispositivoResponse(
        UUID deviceId, String tenantRef, UUID pacienteId, String pairingState, String status) {}
