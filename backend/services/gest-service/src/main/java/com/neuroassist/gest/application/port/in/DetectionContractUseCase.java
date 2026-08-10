package com.neuroassist.gest.application.port.in;

import java.util.List;

public interface DetectionContractUseCase {

    ValidationResult validateReport(ValidateReportCommand command);

    record ValidateReportCommand(int profileVersion, List<String> tipoGestoIds) {}

    record ValidationResult(boolean valid, String contractVersion) {}
}
