package com.neuroassist.dash.infrastructure.clients.adapter;

import com.neuroassist.dash.application.port.out.IdentityDirectoryPort;
import com.neuroassist.dash.infrastructure.clients.AcsClient;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
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
    @CircuitBreaker(name = "acs-service")
    public List<String> getPatientIdsForCaregiver(String caregiverId) {
        log.debug("Consultando ACS: escopo de pacientes do cuidador [{}]", caregiverId);
        AcsClient.PatientScopeResponse scope = acsClient.getPatientScope(caregiverId);
        // Escopo vazio é uma resposta legítima (cuidadora sem paciente vinculado), não uma falha.
        return scope.pacienteIds() == null ? List.of() : scope.pacienteIds();
    }
}
