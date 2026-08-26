package com.neuroassist.dash.application.port.out;

import com.neuroassist.dash.domain.model.DeviceStatus;

/**
 * Outbound port — consulta de liveness ao EDGE para o dispositivo associado ao paciente. Degradacao
 * soft: retorna status com {@code degraded=true} em vez de propagar excecao.
 */
public interface DeviceLivenessPort {

    /** Retorna o status de liveness do dispositivo edge do paciente. */
    DeviceStatus getDeviceStatus(String patientId);
}
