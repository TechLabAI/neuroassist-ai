package com.neuroassist.evnt.application.port.out;

import java.util.List;

public interface GestValidationPort {

    boolean validate(GestValidationRequest request);

    record GestValidationRequest(int profileVersion, List<String> tipoGestoIds) {}
}
