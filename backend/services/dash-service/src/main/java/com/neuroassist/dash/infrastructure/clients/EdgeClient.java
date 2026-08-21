package com.neuroassist.dash.infrastructure.clients;

import com.neuroassist.dash.infrastructure.clients.dto.DeviceStatusResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client para o EDGE (Integracao de Dispositivo Edge). Consulta o status de liveness do
 * dispositivo associado ao paciente. RULE-06: FeignClient apenas em infrastructure.clients.
 */
@FeignClient(name = "edge-service", url = "${dash.edge.base-url:}", contextId = "edgeClient")
public interface EdgeClient {

    @GetMapping("/api/v1/devices/patient/{patientId}/liveness")
    DeviceStatusResponse getDeviceLiveness(@PathVariable("patientId") String patientId);
}
