package com.neuroassist.evnt.application.port.out;

import java.util.List;

/**
 * Directory questions evnt-service asks acs-service.
 *
 * <p>This port used to have a single method, {@code String resolveCaregiver(String pacienteId)},
 * which {@code ReportIngressService} called with a patient id and {@code EventConfirmationService}
 * called with a caregiver id. Two different questions answered by one signature — harmless only
 * while the implementation was {@code CaregiverRoutingHardcodedAdapter}, which returned the same
 * constant for either argument. Against the real directory they are separate endpoints.
 */
public interface CaregiverRoutingPort {

    /**
     * Caregivers responsible for a patient — the routing targets of an alarm.
     *
     * @return possibly empty; an empty list means nobody is on the hook for this patient, which is
     *     a routing outcome the caller must handle, not an error
     */
    List<ResponsibleCaregiver> responsibleFor(String pacienteId);

    /**
     * Whether acs-service knows this caregiver — used to validate whoever confirms an Evento
     * (EVNT-023).
     */
    boolean isKnownCaregiver(String caregiverId);

    record ResponsibleCaregiver(String cuidadorId, String papel) {}
}
