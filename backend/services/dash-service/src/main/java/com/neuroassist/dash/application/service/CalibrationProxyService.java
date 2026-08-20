package com.neuroassist.dash.application.service;

import com.neuroassist.dash.application.port.in.CalibrationProxyUseCase;
import com.neuroassist.dash.application.port.out.CommandCatalogPort;
import com.neuroassist.dash.application.port.out.IdentityDirectoryPort;
import com.neuroassist.dash.domain.model.CalibrationParams;
import com.neuroassist.dash.domain.model.CalibrationResult;
import com.neuroassist.dash.shared.error.PatientNotInScopeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implementa o write-through de calibracao: valida escopo via ACS, delega ao PAC,
 * retorna resultado. DASH nao armazena nada — puro proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CalibrationProxyService implements CalibrationProxyUseCase {

    private final IdentityDirectoryPort identityDirectoryPort;
    private final CommandCatalogPort commandCatalogPort;

    @Override
    public CalibrationResult calibrate(
            String caregiverId,
            String patientId,
            String commandId,
            CalibrationParams params,
            String tenantId) {

        log.info(
                "calibrate caregiverId={} patientId={} commandId={} tenantId={}",
                caregiverId, patientId, commandId, tenantId);

        var allowedPatients = identityDirectoryPort.getPatientIdsForCaregiver(caregiverId);
        if (!allowedPatients.contains(patientId)) {
            log.warn("Scope violation: caregiverId={} patientId={} not in scope", caregiverId, patientId);
            throw new PatientNotInScopeException(patientId);
        }

        // Delega ao PAC — hard dependency: erro e propagado (503)
        CalibrationResult result = commandCatalogPort.calibrate(patientId, commandId, params);
        log.info(
                "calibrate result commandId={} state={} success={}",
                result.commandId(), result.newCalibrationState(), result.success());
        return result;
    }
}
