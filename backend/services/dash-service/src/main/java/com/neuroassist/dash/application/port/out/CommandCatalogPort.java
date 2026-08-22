package com.neuroassist.dash.application.port.out;

import com.neuroassist.dash.domain.model.CalibrationParams;
import com.neuroassist.dash.domain.model.CalibrationResult;
import com.neuroassist.dash.domain.model.CommandCatalogSnapshot;

/**
 * Outbound port — consulta ao PAC para catalogo de comandos, estado de calibracao e write-through
 * de calibracao.
 */
public interface CommandCatalogPort {

    /** Retorna o snapshot do catalogo de comandos do paciente. */
    CommandCatalogSnapshot getCommandCatalog(String patientId);

    /** Retorna o estado atual de calibracao do paciente. */
    String getCalibrationState(String patientId);

    /** Envia calibracao para o PAC e retorna o resultado (write-through). */
    CalibrationResult calibrate(String patientId, String commandId, CalibrationParams params);
}
