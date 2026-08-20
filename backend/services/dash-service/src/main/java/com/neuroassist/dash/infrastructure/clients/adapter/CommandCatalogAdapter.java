package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.CommandCatalogPort;
import com.neuroassist.dash.domain.model.CalibrationParams;
import com.neuroassist.dash.domain.model.CalibrationResult;
import com.neuroassist.dash.domain.model.CommandCatalogSnapshot;
import com.neuroassist.dash.infrastructure.clients.PacClient;
import com.neuroassist.dash.infrastructure.clients.dto.CalibrationRequestBody;
import com.neuroassist.dash.infrastructure.clients.dto.CalibrationResultResponse;
import com.neuroassist.dash.infrastructure.clients.dto.CommandCatalogResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link CommandCatalogPort} delegando ao {@link PacClient}.
 * Consulta catalogo de comandos, estado de calibracao e executa write-through de calibracao via PAC.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CommandCatalogAdapter implements CommandCatalogPort {

    private final PacClient pacClient;

    @Override
    public CommandCatalogSnapshot getCommandCatalog(String patientId) {
        log.debug("Consultando PAC: catalogo de comandos do paciente [{}]", patientId);
        CommandCatalogResponse response = pacClient.getCommandCatalog(patientId);
        return new CommandCatalogSnapshot(response.commands(), response.calibrationState());
    }

    @Override
    public String getCalibrationState(String patientId) {
        log.debug("Consultando PAC: estado de calibracao do paciente [{}]", patientId);
        return pacClient.getCalibration(patientId).state();
    }

    @Override
    public CalibrationResult calibrate(String patientId, String commandId, CalibrationParams params) {
        log.debug("Write-through PAC: calibracao paciente [{}] comando [{}]", patientId, commandId);
        CalibrationRequestBody body = new CalibrationRequestBody(
                params.sensitivity(),
                params.threshold(),
                params.extra());
        CalibrationResultResponse response = pacClient.calibrate(patientId, commandId, body);
        return new CalibrationResult(
                response.commandId(),
                response.newCalibrationState(),
                response.success());
    }
}
