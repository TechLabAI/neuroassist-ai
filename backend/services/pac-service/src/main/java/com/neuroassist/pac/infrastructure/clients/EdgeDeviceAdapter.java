package com.neuroassist.pac.infrastructure.clients;

import com.neuroassist.pac.application.port.out.EdgeDevicePort;
import com.neuroassist.pac.domain.model.DispositivoEdgeRef;
import com.neuroassist.pac.shared.error.CodedException;
import feign.FeignException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * INT-PAC-003 — Adapter implementing {@link EdgeDevicePort} via {@link EdgeDeviceClient}.
 *
 * <p>Hexagonal rules:
 *
 * <ul>
 *   <li>RULE-03: no {@code @Transactional} here — boundary is in application.service.
 *   <li>RULE-06: @FeignClient is on the client interface, not this adapter.
 *   <li>RULE-04/05: no api.* cross-dependency.
 * </ul>
 *
 * <p>Failure semantics per SPEC-10 §6.3:
 *
 * <ul>
 *   <li>404 from EDGE → {@code CodedException("pac.dispositivo.not-found")} → HTTP 422.
 *   <li>Any other Feign exception → {@code CodedException("edge.unavailable")} → HTTP 503.
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EdgeDeviceAdapter implements EdgeDevicePort {

    private final EdgeDeviceClient edgeDeviceClient;

    @Override
    public DispositivoEdgeRef resolveDispositivo(UUID dispositivoId) {
        try {
            EdgeDispositivoResponse resp = edgeDeviceClient.getDispositivo(dispositivoId);
            return new DispositivoEdgeRef(resp.id(), resp.pacienteId(), resp.empresaId());
        } catch (FeignException.NotFound e) {
            log.warn("Device not found in EDGE dispositivoId={}", dispositivoId);
            throw new CodedException(
                    "pac.dispositivo.not-found", "Device not found in EDGE: " + dispositivoId);
        } catch (FeignException e) {
            log.error("EDGE unavailable while resolving dispositivo={}", dispositivoId, e);
            throw new CodedException(
                    "edge.unavailable", "EDGE service unavailable — cannot resolve device");
        }
    }
}
