package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.IdentityDirectoryPort;
import com.neuroassist.dash.infrastructure.clients.AcsClient;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Adapter outbound: implementa {@link IdentityDirectoryPort} delegando ao {@link AcsClient}.
 * Resolve a lista de pacientes vinculados a um cuidador via ACS.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IdentityDirectoryAdapter implements IdentityDirectoryPort {

    private final AcsClient acsClient;

    @Override
    public List<String> getPatientIdsForCaregiver(String caregiverId) {
        log.debug("Consultando ACS: pacientes do cuidador [{}]", caregiverId);
        return acsClient.getPatientsForCaregiver(caregiverId);
    }
}
