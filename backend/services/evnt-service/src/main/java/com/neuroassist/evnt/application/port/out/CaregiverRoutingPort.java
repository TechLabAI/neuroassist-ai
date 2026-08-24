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

    /**
     * Coordenadoras <b>da clínica</b> — o grau 3 da escada do ADR-011.
     *
     * <p>A coordenadora responde pelo tenant, não por um paciente, então ela não sai de {@link
     * #responsibleFor(String)} a menos que também esteja vinculada àquele paciente. Esta é a
     * pergunta separada.
     *
     * @param tenantId o tenant do alarme (UUID em texto)
     * @return possivelmente vazia. <b>Nunca lança</b>: um grau que não pôde ser resolvido faz o
     *     alarme seguir para o grau seguinte, e nunca encerra a escada
     */
    List<ResponsibleCaregiver> clinicCoordinators(String tenantId);

    record ResponsibleCaregiver(String cuidadorId, String papel) {}
}
