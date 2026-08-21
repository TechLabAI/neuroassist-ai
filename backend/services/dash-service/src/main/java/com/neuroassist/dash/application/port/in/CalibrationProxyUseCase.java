package com.neuroassist.dash.application.port.in;

import com.neuroassist.dash.domain.model.CalibrationParams;
import com.neuroassist.dash.domain.model.CalibrationResult;

/** Inbound port — proxy de calibracao para o PAC. */
public interface CalibrationProxyUseCase {

    CalibrationResult calibrate(
            String caregiverId,
            String patientId,
            String commandId,
            CalibrationParams params,
            String tenantId);
}
