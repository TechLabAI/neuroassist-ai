package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/** Thrown when the referenced patient does not exist in PAC service. Maps to HTTP 422. */
public class UnknownPatientException extends CodedException {

    public UnknownPatientException(UUID pacienteId) {
        super("acs.link.unknown-patient", "Patient not found in PAC service");
        withContext(Map.of("pacienteId", pacienteId.toString()));
    }
}
