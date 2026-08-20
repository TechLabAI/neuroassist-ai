package com.neuroassist.dash.shared.error;

/**
 * Lancada quando o cuidador tenta acessar um paciente que nao esta no seu escopo.
 * Mapeada para HTTP 403 pelo GlobalExceptionHandler.
 */
public class PatientNotInScopeException extends CodedException {

    public PatientNotInScopeException(String patientId) {
        super("PATIENT_NOT_IN_SCOPE", "Patient " + patientId + " not in caregiver scope");
    }
}
