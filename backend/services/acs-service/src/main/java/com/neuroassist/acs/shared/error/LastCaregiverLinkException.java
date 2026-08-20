package com.neuroassist.acs.shared.error;

import java.util.Map;
import java.util.UUID;

/**
 * Thrown when revoking the last active caregiver link for a patient. SAFETY-CRITICAL (D-204): a
 * patient must always have at least one active caregiver. If the last link were revoked, alarm
 * events would have no routing destination. Maps to HTTP 409.
 */
public class LastCaregiverLinkException extends CodedException {

    public LastCaregiverLinkException(UUID pacienteId) {
        super(
                "acs.link.last-caregiver-link",
                "Cannot revoke the last active caregiver link for patient "
                        + pacienteId
                        + " — patient must always have at least one active caregiver (D-204)");
        withContext(Map.of("pacienteId", pacienteId.toString()));
    }
}
