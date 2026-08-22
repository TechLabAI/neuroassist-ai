package com.neuroassist.dash.application.port.out;

import java.util.List;

/** Outbound port — consulta ao ACS para resolver pacientes vinculados a um cuidador. */
public interface IdentityDirectoryPort {

    /** Retorna os IDs de pacientes vinculados ao cuidador no tenant corrente. */
    List<String> getPatientIdsForCaregiver(String caregiverId);
}
