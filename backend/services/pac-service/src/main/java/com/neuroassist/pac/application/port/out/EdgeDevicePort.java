package com.neuroassist.pac.application.port.out;

import com.neuroassist.pac.domain.model.DispositivoEdgeRef;
import java.util.UUID;

/**
 * Outbound port — EDGE device integration (INT-PAC-003 / SPEC-10 §6).
 *
 * <p>Used to resolve DispositivoEdge references when creating or updating a Paciente.
 * Implementations live in {@code infrastructure.clients} (RULE-06).
 *
 * <p>Failure semantics: 404 from EDGE → throw CodedException("pac.dispositivo.not-found", 422);
 * EDGE unreachable → throw CodedException("edge.unavailable", 503).
 */
public interface EdgeDevicePort {

    /**
     * Resolves an Edge device by id against the EDGE service.
     *
     * @throws com.neuroassist.pac.shared.error.CodedException code="pac.dispositivo.not-found"
     *     (422) if the device is unknown.
     * @throws com.neuroassist.pac.shared.error.CodedException code="edge.unavailable" (503) if EDGE
     *     is unreachable.
     */
    DispositivoEdgeRef resolveDispositivo(UUID dispositivoId);
}
