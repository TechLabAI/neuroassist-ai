package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.DeviceLivenessPort;
import com.neuroassist.dash.domain.model.DeviceStatus;
import com.neuroassist.dash.infrastructure.clients.EdgeClient;
import com.neuroassist.dash.infrastructure.clients.dto.DeviceStatusResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link DeviceLivenessPort} delegando ao {@link EdgeClient}. Consulta
 * liveness do dispositivo edge associado ao paciente. Degradacao soft: qualquer excecao retorna
 * {@link DeviceStatus} com degraded=true (ADR-005).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DeviceLivenessAdapter implements DeviceLivenessPort {

    private static final String DEGRADED_STATUS = "unknown";

    private final EdgeClient edgeClient;

    @Override
    public DeviceStatus getDeviceStatus(String patientId) {
        log.debug("Consultando EDGE: liveness do dispositivo do paciente [{}]", patientId);
        try {
            DeviceStatusResponse response = edgeClient.getDeviceLiveness(patientId);
            return new DeviceStatus(response.deviceId(), response.status(), response.degraded());
        } catch (Exception ex) {
            log.warn(
                    "EDGE indisponivel para paciente [{}] — retornando status degradado: {}",
                    patientId,
                    ex.getMessage());
            return new DeviceStatus(null, DEGRADED_STATUS, true);
        }
    }
}
