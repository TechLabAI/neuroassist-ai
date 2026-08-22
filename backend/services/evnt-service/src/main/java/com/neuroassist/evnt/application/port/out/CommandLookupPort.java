package com.neuroassist.evnt.application.port.out;

/**
 * Command snapshot lookup against pac-service.
 *
 * <p>The snapshot is taken at report time and copied into the Evento on purpose: the alarm a
 * caregiver sees, and the record of what was asked, must not change if the command is later
 * recalibrated or retired.
 */
public interface CommandLookupPort {

    /**
     * Resolves the command's label and urgency.
     *
     * <p>Takes the patient id as well because pac-service exposes commands only nested under their
     * patient ({@code /api/pac/v1/pacientes/&#123;pacienteId&#125;/comandos/&#123;id&#125;}) —
     * there is no lookup by command id alone.
     */
    CommandSnapshot lookup(String pacienteId, String commandRef);

    record CommandSnapshot(String commandRef, String label, String urgencia) {}
}
