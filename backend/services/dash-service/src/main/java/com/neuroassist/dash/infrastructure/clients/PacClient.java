package com.neuroassist.dash.infrastructure.clients;

import com.neuroassist.dash.infrastructure.clients.dto.CalibrationRequestBody;
import com.neuroassist.dash.infrastructure.clients.dto.CalibrationResultResponse;
import com.neuroassist.dash.infrastructure.clients.dto.CalibrationStateResponse;
import com.neuroassist.dash.infrastructure.clients.dto.CommandCatalogResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client para o PAC (Pacientes e Catalogo de Comandos).
 * Consulta catalogo de comandos, estado de calibracao e executa write-through de calibracao.
 * RULE-06: FeignClient apenas em infrastructure.clients.
 */
@FeignClient(name = "pac-service", url = "${dash.pac.base-url:}", contextId = "pacClient")
public interface PacClient {

    @GetMapping("/api/v1/patients/{patientId}/commands")
    CommandCatalogResponse getCommandCatalog(@PathVariable("patientId") String patientId);

    @GetMapping("/api/v1/patients/{patientId}/calibration")
    CalibrationStateResponse getCalibration(@PathVariable("patientId") String patientId);

    @PostMapping("/api/v1/patients/{patientId}/commands/{commandId}/calibrate")
    CalibrationResultResponse calibrate(
            @PathVariable("patientId") String patientId,
            @PathVariable("commandId") String commandId,
            @RequestBody CalibrationRequestBody body);
}
