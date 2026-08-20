package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/** Thrown when a caregiver (cuidador) with the given ID does not exist. Maps to HTTP 404. */
public class CaregiverNotFoundException extends CodedException {

    public CaregiverNotFoundException(UUID cuidadorId) {
        super("acs.cuidador.not-found", "Caregiver not found: " + cuidadorId);
        withContext(Map.of("cuidadorId", cuidadorId.toString()));
    }
}
